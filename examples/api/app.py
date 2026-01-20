#!/usr/bin/env python3
"""
CICFlowMeter API 示例服务
接收流量特征数据并提供REST API
"""

from flask import Flask, request, jsonify
import json
import logging
from datetime import datetime
import os

app = Flask(__name__)

# 配置日志
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

# 数据存储
received_flows = []
stats = {
    "total_flows": 0,
    "last_received": None,
    "start_time": datetime.now().isoformat(),
}


@app.route("/health", methods=["GET"])
def health_check():
    """健康检查端点"""
    return jsonify(
        {
            "status": "healthy",
            "service": "cicflowmeter-api",
            "timestamp": datetime.now().isoformat(),
            "stats": stats,
        }
    )


@app.route("/api/v1/process_stream", methods=["POST"])
def receive_flows():
    """接收流量特征数据"""
    global stats

    try:
        data = request.get_json()
        if not data:
            return jsonify({"error": "No JSON data provided"}), 400

        # 记录接收到的数据
        flow_count = len(data) if isinstance(data, list) else 1
        logger.info(f"Received {flow_count} flow(s)")

        # 添加到存储
        if isinstance(data, list):
            received_flows.extend(data)
        else:
            received_flows.append(data)

        # 更新统计信息
        stats["total_flows"] += flow_count
        stats["last_received"] = datetime.now().isoformat()

        # 保存到文件（可选）
        save_to_file(data)

        return jsonify(
            {
                "status": "success",
                "received": flow_count,
                "total": stats["total_flows"],
                "timestamp": stats["last_received"],
            }
        ), 200

    except Exception as e:
        logger.error(f"Error processing flows: {e}")
        return jsonify({"error": str(e)}), 500


@app.route("/api/v1/flows", methods=["GET"])
def get_flows():
    """获取存储的流量数据"""
    limit = request.args.get("limit", default=100, type=int)
    offset = request.args.get("offset", default=0, type=int)

    flows_to_return = received_flows[offset : offset + limit]

    return jsonify(
        {
            "flows": flows_to_return,
            "count": len(flows_to_return),
            "total": len(received_flows),
            "offset": offset,
            "limit": limit,
            "stats": stats,
        }
    )


@app.route("/stats", methods=["GET"])
def get_stats():
    """获取统计信息"""
    return jsonify(stats)


@app.route("/reset", methods=["POST"])
def reset_data():
    """重置数据（仅用于测试）"""
    global received_flows, stats

    received_flows.clear()
    stats = {
        "total_flows": 0,
        "last_received": None,
        "start_time": datetime.now().isoformat(),
    }

    return jsonify({"status": "reset", "timestamp": datetime.now().isoformat()})


def save_to_file(data):
    """保存数据到文件（可选）"""
    try:
        log_dir = "/app/logs"
        os.makedirs(log_dir, exist_ok=True)

        today = datetime.now().strftime("%Y-%m-%d")
        filename = os.path.join(log_dir, f"flows_{today}.jsonl")

        with open(filename, "a") as f:
            if isinstance(data, list):
                for item in data:
                    f.write(json.dumps(item) + "\n")
            else:
                f.write(json.dumps(data) + "\n")

        logger.debug(f"Saved data to {filename}")
    except Exception as e:
        logger.warning(f"Failed to save data to file: {e}")


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
