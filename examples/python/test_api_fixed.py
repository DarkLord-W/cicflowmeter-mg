#!/usr/bin/env python3
"""
CICFlowMeter API 测试服务器 - 修复增强版
优化Web界面，支持完整功能，修复转义问题
运行: python3 test_api_fixed.py
"""

from flask import Flask, request, jsonify, send_file
import json
import threading
import time
import os
import csv
import datetime
from io import StringIO, BytesIO

app = Flask(__name__)
received_data = []
received_count = 0
SAVE_DIR = "received_data"
os.makedirs(SAVE_DIR, exist_ok=True)


@app.route("/flows", methods=["POST"])
def predict():
    global received_count
    try:
        data = request.get_json()
        received_data.append(data)
        received_count += 1

        # 保存到文件
        filename = f"{SAVE_DIR}/data_{received_count:04d}.json"
        with open(filename, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)

        print(f"\n{'=' * 80}")
        print(f"收到第{received_count}条数据:")
        print(f"  Timestamp: {data.get('timestamp')}")
        print(f"  已保存到: {filename}")

        # 如果有结构化数据，显示完整特征
        if "structured" in data:
            structured = data["structured"]
            print(f"\n  {'-' * 40} 结构化特征 (共{len(structured)}个) {'-' * 40}")

            # 按特征类别分组显示
            flow_features = []
            packet_features = []
            other_features = []

            for key, value in structured.items():
                if "Duration" in key or "Fwd" in key or "Bwd" in key or "Flow" in key:
                    flow_features.append((key, value))
                elif "Packet" in key or "Pkt" in key:
                    packet_features.append((key, value))
                else:
                    other_features.append((key, value))

            # 显示流特征
            print(f"\n  流特征:")
            for key, value in flow_features[:10]:  # 显示前10个
                print(f"    {key:30}: {value}")
            if len(flow_features) > 10:
                print(f"    ... 和其他 {len(flow_features) - 10} 个流特征")

            # 显示包特征
            if packet_features:
                print(f"\n  包特征:")
                for key, value in packet_features[:5]:  # 显示前5个
                    print(f"    {key:30}: {value}")
                if len(packet_features) > 5:
                    print(f"    ... 和其他 {len(packet_features) - 5} 个包特征")

            # 显示其他重要特征
            important_keys = [
                "Label",
                "SrcIP",
                "DstIP",
                "SrcPort",
                "DstPort",
                "Protocol",
            ]
            important_features = [
                (k, structured.get(k)) for k in important_keys if k in structured
            ]

            if important_features:
                print(f"\n  其他重要特征:")
                for key, value in important_features:
                    if value is not None:
                        print(f"    {key:30}: {value}")
        else:
            print(f"\n  CSV数据:")
            print(f"    Header: {data.get('csv_header', '')}")
            print(f"    Data: {data.get('flow_data', '')[:100]}...")

        print(f"\n{'=' * 80}")
        return jsonify(
            {
                "status": "success",
                "message": f"已接收第{received_count}条数据",
                "saved_to": filename,
            }
        ), 200
    except Exception as e:
        print(f"错误: {e}")
        return jsonify({"status": "error", "message": str(e)}), 400


@app.route("/", methods=["GET"])
def index():
    """Web界面查看所有接收的数据 - 修复版"""
    # 构建HTML模板，使用占位符
    html_template = """<!DOCTYPE html>
<html>
<head>
    <title>CICFlowMeter API 数据接收器 - 增强版</title>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css" rel="stylesheet">
    <link href="https://cdnjs.cloudflare.com/ajax/libs/bootstrap/5.1.3/css/bootstrap.min.css" rel="stylesheet">
    <style>
        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f5f5f5; }
        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 2rem 0; margin-bottom: 2rem; }
        .card { border-radius: 10px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); margin-bottom: 1.5rem; }
        .card-header { background: #f8f9fa; border-bottom: 1px solid rgba(0,0,0,.125); }
        .btn-group-sm .btn { border-radius: 5px; }
        .table thead th { border-top: none; background: #f8f9fa; font-weight: 600; cursor: pointer; user-select: none; }
        .table thead th:hover { background: #e9ecef; }
        .table tbody tr:hover { background: rgba(0,123,255,0.05); }
        .badge { font-size: 0.85em; }
        .json-viewer { font-family: 'Courier New', monospace; font-size: 0.9em; background: #f8f9fa; border-radius: 5px; max-height: 400px; overflow-y: auto; }
        .search-box { max-width: 300px; }
        .pagination { margin-bottom: 0; }
        .stats-card .card-body { padding: 1rem; }
        .stats-value { font-size: 1.5rem; font-weight: bold; color: #007bff; }
        .stats-label { font-size: 0.85rem; color: #6c757d; text-transform: uppercase; }
        .refresh-animation { animation: spin 1s linear infinite; }
        @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
        .modal-lg { max-width: 90%; }
        .feature-badge { margin-right: 5px; margin-bottom: 5px; }
        .loading-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(255,255,255,0.8); z-index: 9999; display: none; }
        .loading-spinner { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); }
    </style>
</head>
<body>
    <!-- 加载遮罩 -->
    <div class="loading-overlay" id="loadingOverlay">
        <div class="loading-spinner">
            <div class="spinner-border text-primary" style="width: 3rem; height: 3rem;" role="status">
                <span class="visually-hidden">加载中...</span>
            </div>
        </div>
    </div>

    <div class="container-fluid py-3">
        <!-- 头部 -->
        <div class="header">
            <div class="container">
                <div class="row align-items-center">
                    <div class="col-md-8">
                        <h1 class="display-5 fw-bold"><i class="fas fa-network-wired me-2"></i>CICFlowMeter API 数据接收器</h1>
                        <p class="lead mb-0">实时接收、分析和展示网络流量特征数据</p>
                    </div>
                    <div class="col-md-4 text-md-end">
                        <div class="btn-group">
                            <button class="btn btn-light" onclick="refreshData()" title="刷新数据">
                                <i class="fas fa-sync-alt"></i> 刷新
                            </button>
                            <button class="btn btn-success" onclick="exportCSV()" title="导出CSV">
                                <i class="fas fa-file-csv"></i> CSV
                            </button>
                            <button class="btn btn-info" onclick="exportJSON()" title="导出JSON">
                                <i class="fas fa-file-code"></i> JSON
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- 统计卡片 -->
        <div class="row mb-4" id="statsRow">
            <div class="col-md-3">
                <div class="card stats-card">
                    <div class="card-body text-center">
                        <div class="stats-value" id="totalCount">0</div>
                        <div class="stats-label">总接收数据</div>
                    </div>
                </div>
            </div>
            <div class="col-md-3">
                <div class="card stats-card">
                    <div class="card-body text-center">
                        <div class="stats-value" id="inMemoryCount">0</div>
                        <div class="stats-label">内存数据</div>
                    </div>
                </div>
            </div>
            <div class="col-md-3">
                <div class="card stats-card">
                    <div class="card-body text-center">
                        <div class="stats-value" id="structuredCount">0</div>
                        <div class="stats-label">结构化数据</div>
                    </div>
                </div>
            </div>
            <div class="col-md-3">
                <div class="card stats-card">
                    <div class="card-body text-center">
                        <div class="stats-value" id="lastUpdate">刚刚</div>
                        <div class="stats-label">最后更新</div>
                    </div>
                </div>
            </div>
        </div>

        <!-- 控制面板 -->
        <div class="card mb-4">
            <div class="card-header d-flex justify-content-between align-items-center">
                <h5 class="mb-0"><i class="fas fa-sliders-h me-2"></i>控制面板</h5>
                <div class="btn-group">
                    <button class="btn btn-sm btn-primary" onclick="loadData()">
                        <i class="fas fa-redo me-1"></i>重新加载
                    </button>
                    <button class="btn btn-sm btn-danger" onclick="clearAllData()">
                        <i class="fas fa-trash-alt me-1"></i>清空数据
                    </button>
                    <button class="btn btn-sm btn-success" onclick="showStats()">
                        <i class="fas fa-chart-bar me-1"></i>详细统计
                    </button>
                </div>
            </div>
            <div class="card-body">
                <div class="row g-3">
                    <div class="col-md-3">
                        <label class="form-label">标签过滤</label>
                        <select class="form-select" id="labelFilter" onchange="loadData()">
                            <option value="">全部标签</option>
                            <option value="BENIGN">BENIGN</option>
                            <option value="Malicious">Malicious</option>
                            <option value="Attack">Attack</option>
                            <option value="NeedManualLabel">NeedManualLabel</option>
                        </select>
                    </div>
                    <div class="col-md-3">
                        <label class="form-label">协议过滤</label>
                        <select class="form-select" id="protocolFilter" onchange="loadData()">
                            <option value="">全部协议</option>
                            <option value="TCP">TCP</option>
                            <option value="UDP">UDP</option>
                            <option value="ICMP">ICMP</option>
                            <option value="HTTP">HTTP</option>
                        </select>
                    </div>
                    <div class="col-md-4">
                        <label class="form-label">搜索</label>
                        <div class="input-group">
                            <input type="text" class="form-control" id="searchInput" placeholder="搜索IP、特征值等...">
                            <button class="btn btn-outline-secondary" onclick="loadData()">
                                <i class="fas fa-search"></i>
                            </button>
                        </div>
                    </div>
                    <div class="col-md-2">
                        <label class="form-label">每页显示</label>
                        <select class="form-select" id="perPage" onchange="loadData()">
                            <option value="10">10条</option>
                            <option value="20" selected>20条</option>
                            <option value="50">50条</option>
                            <option value="100">100条</option>
                        </select>
                    </div>
                </div>
            </div>
        </div>

        <!-- 数据表格 -->
        <div class="card">
            <div class="card-header d-flex justify-content-between align-items-center">
                <h5 class="mb-0"><i class="fas fa-table me-2"></i>接收数据列表</h5>
                <span class="text-muted" id="tableSummary">加载中...</span>
            </div>
            <div class="card-body p-0">
                <div class="table-responsive">
                    <table class="table table-hover mb-0" id="dataTable">
                        <thead>
                            <tr>
                                <th onclick="sortTable(0)">ID <i class="fas fa-sort"></i></th>
                                <th onclick="sortTable(1)">时间戳 <i class="fas fa-sort"></i></th>
                                <th onclick="sortTable(2)">标签 <i class="fas fa-sort"></i></th>
                                <th onclick="sortTable(3)">源IP <i class="fas fa-sort"></i></th>
                                <th onclick="sortTable(4)">目标IP <i class="fas fa-sort"></i></th>
                                <th onclick="sortTable(5)">协议 <i class="fas fa-sort"></i></th>
                                <th onclick="sortTable(6)">特征数 <i class="fas fa-sort"></i></th>
                                <th>操作</th>
                            </tr>
                        </thead>
                        <tbody id="tableBody">
                            <tr><td colspan="8" class="text-center py-4">加载数据中...</td></tr>
                        </tbody>
                    </table>
                </div>
            </div>
            <div class="card-footer">
                <nav aria-label="数据分页">
                    <ul class="pagination justify-content-center mb-0" id="pagination">
                        <li class="page-item disabled"><a class="page-link" href="#">上一页</a></li>
                        <li class="page-item active"><a class="page-link" href="#">1</a></li>
                        <li class="page-item disabled"><a class="page-link" href="#">下一页</a></li>
                    </ul>
                </nav>
            </div>
        </div>

        <!-- 数据详情模态框 -->
        <div class="modal fade" id="detailModal" tabindex="-1" aria-hidden="true">
            <div class="modal-dialog modal-lg">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title" id="detailModalTitle">数据详情</h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                    </div>
                    <div class="modal-body">
                        <div class="mb-3">
                            <ul class="nav nav-tabs" id="detailTabs">
                                <li class="nav-item">
                                    <button class="nav-link active" data-bs-toggle="tab" data-bs-target="#jsonTab">JSON格式</button>
                                </li>
                                <li class="nav-item">
                                    <button class="nav-link" data-bs-toggle="tab" data-bs-target="#featuresTab">特征列表</button>
                                </li>
                                <li class="nav-item">
                                    <button class="nav-link" data-bs-toggle="tab" data-bs-target="#rawTab">原始数据</button>
                                </li>
                            </ul>
                            <div class="tab-content mt-3">
                                <div class="tab-pane fade show active" id="jsonTab">
                                    <pre class="json-viewer p-3" id="jsonViewer">{}</pre>
                                </div>
                                <div class="tab-pane fade" id="featuresTab">
                                    <div id="featuresContainer"></div>
                                </div>
                                <div class="tab-pane fade" id="rawTab">
                                    <pre class="json-viewer p-3" id="rawViewer"></pre>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">关闭</button>
                        <button type="button" class="btn btn-primary" onclick="copyJSON()">
                            <i class="fas fa-copy me-1"></i>复制JSON
                        </button>
                    </div>
                </div>
            </div>
        </div>

        <!-- 统计信息模态框 -->
        <div class="modal fade" id="statsModal" tabindex="-1" aria-hidden="true">
            <div class="modal-dialog modal-lg">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title">详细统计数据</h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                    </div>
                    <div class="modal-body">
                        <!-- 标签页导航 -->
                        <ul class="nav nav-tabs" id="statsTabs" role="tablist">
                            <li class="nav-item" role="presentation">
                                <button class="nav-link active" id="dataStatsTab" data-bs-toggle="tab" 
                                        data-bs-target="#dataStatsContent" type="button" role="tab">
                                    <i class="fas fa-database me-1"></i>数据统计
                                </button>
                            </li>
                            <li class="nav-item" role="presentation">
                                <button class="nav-link" id="healthStatsTab" data-bs-toggle="tab" 
                                        data-bs-target="#healthStatsContent" type="button" role="tab"
                                        onclick="loadHealthStats()">
                                    <i class="fas fa-heartbeat me-1"></i>健康检查
                                </button>
                            </li>
                        </ul>
                        
                        <!-- 标签页内容 -->
                        <div class="tab-content mt-3">
                            <div class="tab-pane fade show active" id="dataStatsContent" role="tabpanel">
                                <div id="statsContent"></div>
                            </div>
                            <div class="tab-pane fade" id="healthStatsContent" role="tabpanel">
                                <div id="healthStatsContent">
                                    <div class="text-center py-4">
                                        <div class="spinner-border text-primary" role="status">
                                            <span class="visually-hidden">加载健康检查数据中...</span>
                                        </div>
                                        <p class="mt-2">正在获取健康检查信息...</p>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <!-- JavaScript库 -->
    <script src="https://cdnjs.cloudflare.com/ajax/libs/bootstrap/5.1.3/js/bootstrap.bundle.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/jquery/3.6.0/jquery.min.js"></script>
    <script>
        // 全局变量
        let currentPage = 1;
        let currentSortColumn = 0;
        let currentSortDirection = 'asc';
        let allData = [];
        let filteredData = [];

        // 显示加载动画
        function showLoading() {
            document.getElementById('loadingOverlay').style.display = 'block';
        }

        // 隐藏加载动画
        function hideLoading() {
            document.getElementById('loadingOverlay').style.display = 'none';
        }

        // 刷新统计数据
        function refreshStats() {
            fetch('/api/stats')
                .then(response => response.json())
                .then(data => {
                    if (data.status === 'success') {
                        document.getElementById('totalCount').textContent = data.total_count;
                        document.getElementById('inMemoryCount').textContent = data.in_memory_count;
                        document.getElementById('structuredCount').textContent = data.structured_count;
                        document.getElementById('lastUpdate').textContent = '刚刚';
                    }
                })
                .catch(error => console.error('获取统计失败:', error));
        }

        // 加载数据
        function loadData(page = 1) {
            showLoading();
            currentPage = page;
            
            const labelFilter = document.getElementById('labelFilter').value;
            const searchTerm = document.getElementById('searchInput').value;
            const perPage = document.getElementById('perPage').value;
            
            let url = `/api/list?page=${page}&per_page=${perPage}`;
            if (labelFilter) url += `&label=${labelFilter}`;
            if (searchTerm) url += `&search=${searchTerm}`;
            
            fetch(url)
                .then(response => response.json())
                .then(data => {
                    if (data.status === 'success') {
                        allData = data.data;
                        renderTable();
                        renderPagination(data.total_pages);
                        document.getElementById('tableSummary').textContent = 
                            `显示第 ${(page-1)*parseInt(perPage)+1}-${Math.min(page*parseInt(perPage), data.total)} 条，共 ${data.total} 条数据`;
                        refreshStats();
                    }
                    hideLoading();
                })
                .catch(error => {
                    console.error('加载数据失败:', error);
                    hideLoading();
                    alert('加载数据失败: ' + error.message);
                });
        }

        // 渲染表格
        function renderTable() {
            const tbody = document.getElementById('tableBody');
            tbody.innerHTML = '';
            
            if (allData.length === 0) {
                tbody.innerHTML = '<tr><td colspan="8" class="text-center py-4">暂无数据</td></tr>';
                return;
            }
            
            allData.forEach(item => {
                const row = document.createElement('tr');
                
                // 标签样式
                let labelBadge = '';
                if (item.label) {
                    const badgeClass = item.label === 'BENIGN' ? 'bg-success' : 
                                      item.label === 'Malicious' ? 'bg-danger' : 
                                      item.label === 'Attack' ? 'bg-warning' : 'bg-secondary';
                    labelBadge = `<span class="badge ${badgeClass}">${item.label}</span>`;
                }
                
                // 特征数徽章
                const featureBadge = `<span class="badge bg-info">${item.feature_count || 0}</span>`;
                
                row.innerHTML = `
                    <td class="fw-bold">#${item.id}</td>
                    <td><small class="text-muted">${item.timestamp || 'N/A'}</small></td>
                    <td>${labelBadge}</td>
                    <td><code>${item.src_ip || 'N/A'}</code></td>
                    <td><code>${item.dst_ip || 'N/A'}</code></td>
                    <td><span class="badge bg-secondary">${item.protocol || 'N/A'}</span></td>
                    <td>${featureBadge}</td>
                    <td>
                        <button class="btn btn-sm btn-outline-primary" onclick="viewDetail(${item.id})" title="查看详情">
                            <i class="fas fa-eye"></i>
                        </button>
                        <button class="btn btn-sm btn-outline-danger" onclick="deleteData(${item.id})" title="删除">
                            <i class="fas fa-trash"></i>
                        </button>
                    </td>
                `;
                
                tbody.appendChild(row);
            });
        }

        // 渲染分页
        function renderPagination(totalPages) {
            const pagination = document.getElementById('pagination');
            pagination.innerHTML = '';
            
            // 上一页按钮
            const prevDisabled = currentPage <= 1 ? 'disabled' : '';
            pagination.innerHTML += `
                <li class="page-item ${prevDisabled}">
                    <a class="page-link" href="#" onclick="loadData(${currentPage-1})" ${prevDisabled ? 'tabindex="-1"' : ''}>上一页</a>
                </li>
            `;
            
            // 页码按钮
            const maxButtons = 5;
            let startPage = Math.max(1, currentPage - Math.floor(maxButtons/2));
            let endPage = Math.min(totalPages, startPage + maxButtons - 1);
            
            if (endPage - startPage + 1 < maxButtons) {
                startPage = Math.max(1, endPage - maxButtons + 1);
            }
            
            for (let i = startPage; i <= endPage; i++) {
                const active = i === currentPage ? 'active' : '';
                pagination.innerHTML += `
                    <li class="page-item ${active}">
                        <a class="page-link" href="#" onclick="loadData(${i})">${i}</a>
                    </li>
                `;
            }
            
            // 下一页按钮
            const nextDisabled = currentPage >= totalPages ? 'disabled' : '';
            pagination.innerHTML += `
                <li class="page-item ${nextDisabled}">
                    <a class="page-link" href="#" onclick="loadData(${currentPage+1})" ${nextDisabled ? 'tabindex="-1"' : ''}>下一页</a>
                </li>
            `;
        }

        // 表格排序
        function sortTable(columnIndex) {
            if (currentSortColumn === columnIndex) {
                currentSortDirection = currentSortDirection === 'asc' ? 'desc' : 'asc';
            } else {
                currentSortColumn = columnIndex;
                currentSortDirection = 'asc';
            }
            
            // 更新排序图标
            document.querySelectorAll('#dataTable thead th i').forEach((icon, index) => {
                if (index === columnIndex) {
                    icon.className = currentSortDirection === 'asc' ? 'fas fa-sort-up' : 'fas fa-sort-down';
                } else {
                    icon.className = 'fas fa-sort';
                }
            });
            
            // 排序数据
            allData.sort((a, b) => {
                let aValue, bValue;
                
                switch(columnIndex) {
                    case 0: aValue = a.id; bValue = b.id; break;
                    case 1: aValue = a.timestamp; bValue = b.timestamp; break;
                    case 2: aValue = a.label || ''; bValue = b.label || ''; break;
                    case 3: aValue = a.src_ip || ''; bValue = b.src_ip || ''; break;
                    case 4: aValue = a.dst_ip || ''; bValue = b.dst_ip || ''; break;
                    case 5: aValue = a.protocol || ''; bValue = b.protocol || ''; break;
                    case 6: aValue = a.feature_count || 0; bValue = b.feature_count || 0; break;
                    default: return 0;
                }
                
                if (currentSortDirection === 'asc') {
                    return aValue > bValue ? 1 : aValue < bValue ? -1 : 0;
                } else {
                    return aValue < bValue ? 1 : aValue > bValue ? -1 : 0;
                }
            });
            
            renderTable();
        }

        // 查看详情
        function viewDetail(id) {
            showLoading();
            fetch(`/api/data/${id}`)
                .then(response => response.json())
                .then(data => {
                    // 格式化JSON显示
                    document.getElementById('jsonViewer').textContent = JSON.stringify(data, null, 2);
                    document.getElementById('rawViewer').textContent = JSON.stringify(data, null, 2);
                    
                    // 生成特征列表
                    if (data.structured) {
                        const featuresContainer = document.getElementById('featuresContainer');
                        featuresContainer.innerHTML = '';
                        
                        const features = data.structured;
                        const categories = {
                            '基础信息': ['Label', 'SrcIP', 'DstIP', 'SrcPort', 'DstPort', 'Protocol'],
                            '时间特征': Object.keys(features).filter(k => k.includes('Duration') || k.includes('IAT')),
                            '包特征': Object.keys(features).filter(k => k.includes('Packet') || k.includes('Pkt')),
                            '长度特征': Object.keys(features).filter(k => k.includes('Length') || k.includes('Size')),
                            '其他特征': Object.keys(features).filter(k => !k.includes('Duration') && !k.includes('IAT') && 
                                                          !k.includes('Packet') && !k.includes('Pkt') && 
                                                          !k.includes('Length') && !k.includes('Size') &&
                                                          !['Label', 'SrcIP', 'DstIP', 'SrcPort', 'DstPort', 'Protocol'].includes(k))
                        };
                        
                        for (const [category, keys] of Object.entries(categories)) {
                            if (keys.length > 0) {
                                const categoryDiv = document.createElement('div');
                                categoryDiv.className = 'mb-3';
                                categoryDiv.innerHTML = `<h6>${category} <span class="badge bg-secondary">${keys.length}</span></h6>`;
                                
                                const badgesDiv = document.createElement('div');
                                keys.forEach(key => {
                                    const value = features[key];
                                    const badge = document.createElement('span');
                                    badge.className = 'badge bg-light text-dark feature-badge';
                                    badge.style.cursor = 'pointer';
                                    badge.title = `${key}: ${value}`;
                                    badge.innerHTML = `<strong>${key}</strong>: ${value}`;
                                    badge.onclick = () => navigator.clipboard.writeText(value);
                                    badgesDiv.appendChild(badge);
                                });
                                
                                categoryDiv.appendChild(badgesDiv);
                                featuresContainer.appendChild(categoryDiv);
                            }
                        }
                    }
                    
                    document.getElementById('detailModalTitle').textContent = `数据 #${id} 详情`;
                    const modal = new bootstrap.Modal(document.getElementById('detailModal'));
                    modal.show();
                    hideLoading();
                })
                .catch(error => {
                    console.error('获取详情失败:', error);
                    hideLoading();
                    alert('获取详情失败: ' + error.message);
                });
        }

        // 删除数据
        function deleteData(id) {
            if (confirm(`确定要删除数据 #${id} 吗？`)) {
                showLoading();
                fetch(`/api/delete/${id}`, { method: 'DELETE' })
                    .then(response => response.json())
                    .then(data => {
                        if (data.status === 'success') {
                            alert('删除成功！');
                            loadData(currentPage);
                        } else {
                            alert('删除失败: ' + data.message);
                        }
                        hideLoading();
                    })
                    .catch(error => {
                        console.error('删除失败:', error);
                        alert('删除失败: ' + error.message);
                        hideLoading();
                    });
            }
        }

        // 清空所有数据
        function clearAllData() {
            if (confirm('确定要清空所有数据吗？此操作不可撤销！')) {
                showLoading();
                fetch('/api/clear', { method: 'POST' })
                    .then(response => response.json())
                    .then(data => {
                        if (data.status === 'success') {
                            alert('数据已清空！');
                            loadData(1);
                        }
                        hideLoading();
                    })
                    .catch(error => {
                        console.error('清空失败:', error);
                        alert('清空失败: ' + error.message);
                        hideLoading();
                    });
            }
        }

        // 导出CSV
        function exportCSV() {
            window.open('/api/export/csv', '_blank');
        }

        // 导出JSON
        function exportJSON() {
            window.open('/api/export/json', '_blank');
        }

        // 刷新数据
        function refreshData() {
            const refreshBtn = document.querySelector('button[onclick="refreshData()"]');
            refreshBtn.innerHTML = '<i class="fas fa-sync-alt refresh-animation"></i> 刷新中...';
            loadData(currentPage);
            setTimeout(() => {
                refreshBtn.innerHTML = '<i class="fas fa-sync-alt"></i> 刷新';
            }, 1000);
        }

        // 显示统计信息
        function showStats() {
            showLoading();
            fetch('/api/stats')
                .then(response => response.json())
                .then(data => {
                    if (data.status === 'success') {
                        let statsHtml = `
                            <div class="row">
                                <div class="col-md-6">
                                    <div class="card mb-3">
                                        <div class="card-header">数据统计</div>
                                        <div class="card-body">
                                            <table class="table">
                                                <tr><td>总接收数据</td><td class="text-end">${data.total_count}</td></tr>
                                                <tr><td>内存中数据</td><td class="text-end">${data.in_memory_count}</td></tr>
                                                <tr><td>结构化数据</td><td class="text-end">${data.structured_count}</td></tr>
                                                <tr><td>纯CSV数据</td><td class="text-end">${data.csv_only_count}</td></tr>
                                            </table>
                                        </div>
                                    </div>
                                </div>
                                <div class="col-md-6">
                                    <div class="card mb-3">
                                        <div class="card-header">标签分布</div>
                                        <div class="card-body">
                        `;
                        
                        if (Object.keys(data.label_stats).length > 0) {
                            statsHtml += '<table class="table">';
                            for (const [label, count] of Object.entries(data.label_stats)) {
                                const percentage = (count / data.in_memory_count * 100).toFixed(1);
                                statsHtml += `<tr><td>${label}</td><td class="text-end">${count} (${percentage}%)</td></tr>`;
                            }
                            statsHtml += '</table>';
                        } else {
                            statsHtml += '<p class="text-muted">暂无标签数据</p>';
                        }
                        
                        statsHtml += `
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div class="row">
                                <div class="col-md-12">
                                    <div class="card">
                                        <div class="card-header">协议分布</div>
                                        <div class="card-body">
                        `;
                        
                        if (Object.keys(data.protocol_stats).length > 0) {
                            statsHtml += '<table class="table">';
                            for (const [protocol, count] of Object.entries(data.protocol_stats)) {
                                const percentage = (count / data.in_memory_count * 100).toFixed(1);
                                statsHtml += `<tr><td>${protocol}</td><td class="text-end">${count} (${percentage}%)</td></tr>`;
                            }
                            statsHtml += '</table>';
                        } else {
                            statsHtml += '<p class="text-muted">暂无协议数据</p>';
                        }
                        
                        statsHtml += `
                                        </div>
                                    </div>
                                </div>
                            </div>
                        `;
                        
                        document.getElementById('statsContent').innerHTML = statsHtml;
                        const modal = new bootstrap.Modal(document.getElementById('statsModal'));
                        modal.show();
                    }
                    hideLoading();
                })
                .catch(error => {
                    console.error('获取统计失败:', error);
                    hideLoading();
                    alert('获取统计失败: ' + error.message);
                });
        }

        // 加载健康检查数据
        function loadHealthStats() {
            showLoading();
            fetch('/health')
                .then(response => response.json())
                .then(data => {
                    let healthHtml = '';
                    
                    if (data.status === 'healthy' || data.status === 'unhealthy') {
                        const statusClass = data.status === 'healthy' ? 'bg-success' : 'bg-danger';
                        const statusIcon = data.status === 'healthy' ? 'fa-check-circle' : 'fa-exclamation-triangle';
                        
                        healthHtml = `
                            <div class="row">
                                <div class="col-md-12 mb-3">
                                    <div class="card">
                                        <div class="card-header d-flex justify-content-between align-items-center">
                                            <span><i class="fas ${statusIcon} me-2"></i>服务器状态</span>
                                            <span class="badge ${statusClass}">${data.status.toUpperCase()}</span>
                                        </div>
                                        <div class="card-body">
                                            <table class="table">
                                                <tr><td>服务名称</td><td class="text-end">${data.service || 'CICFlowMeter API Server'}</td></tr>
                                                <tr><td>版本</td><td class="text-end">${data.version || '1.0.0'}</td></tr>
                                                <tr><td>时间戳</td><td class="text-end">${data.timestamp || new Date().toISOString()}</td></tr>
                                                <tr><td>运行时间</td><td class="text-end">${data.api?.uptime_seconds ? Math.floor(data.api.uptime_seconds/60) + '分钟' : 'N/A'}</td></tr>
                                            </table>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            
                            <div class="row">
                                <div class="col-md-6 mb-3">
                                    <div class="card">
                                        <div class="card-header"><i class="fas fa-microchip me-2"></i>系统资源</div>
                                        <div class="card-body">
                                            <table class="table">
                                                <tr><td>CPU使用率</td><td class="text-end">${data.system?.cpu_percent ? data.system.cpu_percent.toFixed(1) + '%' : 'N/A'}</td></tr>
                                                <tr><td>内存使用率</td><td class="text-end">${data.system?.memory_percent ? data.system.memory_percent.toFixed(1) + '%' : 'N/A'}</td></tr>
                                                <tr><td>内存总量</td><td class="text-end">${data.system?.memory_total_mb ? data.system.memory_total_mb.toFixed(0) + ' MB' : 'N/A'}</td></tr>
                                                <tr><td>内存使用</td><td class="text-end">${data.system?.memory_used_mb ? data.system.memory_used_mb.toFixed(0) + ' MB' : 'N/A'}</td></tr>
                                                <tr><td>磁盘使用率</td><td class="text-end">${data.system?.disk_percent ? data.system.disk_percent.toFixed(1) + '%' : 'N/A'}</td></tr>
                                                <tr><td>磁盘空间</td><td class="text-end">${data.system?.disk_total_gb ? data.system.disk_total_gb.toFixed(1) + ' GB' : 'N/A'}</td></tr>
                                            </table>
                                        </div>
                                    </div>
                                </div>
                                
                                <div class="col-md-6 mb-3">
                                    <div class="card">
                                        <div class="card-header"><i class="fas fa-cogs me-2"></i>进程信息</div>
                                        <div class="card-body">
                                            <table class="table">
                                                <tr><td>进程ID</td><td class="text-end">${data.process?.pid || 'N/A'}</td></tr>
                                                <tr><td>进程内存</td><td class="text-end">${data.process?.memory_mb ? data.process.memory_mb + ' MB' : 'N/A'}</td></tr>
                                                <tr><td>进程CPU</td><td class="text-end">${data.process?.cpu_percent ? data.process.cpu_percent.toFixed(1) + '%' : 'N/A'}</td></tr>
                                                <tr><td>线程数</td><td class="text-end">${data.process?.threads || 'N/A'}</td></tr>
                                                <tr><td>连接数</td><td class="text-end">${data.process?.connections || 'N/A'}</td></tr>
                                                <tr><td>启动时间</td><td class="text-end">${data.process?.create_time ? new Date(data.process.create_time).toLocaleString() : 'N/A'}</td></tr>
                                            </table>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            
                            <div class="row">
                                <div class="col-md-6 mb-3">
                                    <div class="card">
                                        <div class="card-header"><i class="fas fa-database me-2"></i>API数据统计</div>
                                        <div class="card-body">
                                            <table class="table">
                                                <tr><td>总请求数</td><td class="text-end">${data.api?.total_requests || 0}</td></tr>
                                                <tr><td>内存中数据</td><td class="text-end">${data.api?.data_in_memory || 0}</td></tr>
                                                <tr><td>结构化数据</td><td class="text-end">${data.api?.structured_data || 0}</td></tr>
                                                <tr><td>CSV数据</td><td class="text-end">${(data.api?.data_in_memory || 0) - (data.api?.structured_data || 0)}</td></tr>
                                            </table>
                                        </div>
                                    </div>
                                </div>
                                
                                <div class="col-md-6 mb-3">
                                    <div class="card">
                                        <div class="card-header"><i class="fas fa-plug me-2"></i>依赖状态</div>
                                        <div class="card-body">
                                            <table class="table">
                                                <tr><td>Flask框架</td><td class="text-end"><span class="badge bg-success">正常</span></td></tr>
                                                <tr><td>系统监控</td><td class="text-end">${data.dependencies?.psutil ? '<span class="badge bg-success">正常</span>' : '<span class="badge bg-danger">异常</span>'}</td></tr>
                                                <tr><td>网络连接</td><td class="text-end">${data.dependencies?.network_connectivity ? '<span class="badge bg-success">正常</span>' : '<span class="badge bg-danger">异常</span>'}</td></tr>
                                            </table>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            
                            <div class="row">
                                <div class="col-md-12">
                                    <div class="card">
                                        <div class="card-header"><i class="fas fa-signal me-2"></i>API端点状态</div>
                                        <div class="card-body">
                                            <div class="row">
                        `;
                        
                        // 显示API端点状态
                        if (data.api?.endpoints) {
                            const endpoints = data.api.endpoints;
                            for (const [key, value] of Object.entries(endpoints)) {
                                healthHtml += `
                                    <div class="col-md-4 mb-2">
                                        <div class="card bg-light">
                                            <div class="card-body py-2">
                                                <small class="text-muted">${key}</small><br>
                                                <strong>${value}</strong>
                                            </div>
                                        </div>
                                    </div>
                                `;
                            }
                        }
                        
                        healthHtml += `
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        `;
                    } else {
                        healthHtml = `
                            <div class="alert alert-danger">
                                <i class="fas fa-exclamation-triangle me-2"></i>
                                无法获取健康检查数据: ${data.message || '未知错误'}
                            </div>
                        `;
                    }
                    
                    document.getElementById('healthStatsContent').innerHTML = healthHtml;
                    hideLoading();
                })
                .catch(error => {
                    console.error('获取健康检查数据失败:', error);
                    document.getElementById('healthStatsContent').innerHTML = `
                        <div class="alert alert-danger">
                            <i class="fas fa-exclamation-triangle me-2"></i>
                            获取健康检查数据失败: ${error.message}
                        </div>
                    `;
                    hideLoading();
                });
        }

        // 复制JSON到剪贴板
        function copyJSON() {
            const jsonText = document.getElementById('jsonViewer').textContent;
            navigator.clipboard.writeText(jsonText)
                .then(() => alert('JSON已复制到剪贴板！'))
                .catch(err => console.error('复制失败:', err));
        }

        // 页面加载完成后初始化
        document.addEventListener('DOMContentLoaded', function() {
            // 加载数据
            loadData(1);
            
            // 设置自动刷新
            setInterval(refreshStats, 30000);
            
            // 搜索框回车键支持
            document.getElementById('searchInput').addEventListener('keypress', function(e) {
                if (e.key === 'Enter') loadData(1);
            });
        });
    </script>
</body>
</html>"""

    # 返回完整的HTML
    return html_template


@app.route("/api/all", methods=["GET"])
def get_all_data():
    """获取所有接收的数据"""
    return jsonify({"total_count": received_count, "data": received_data})


@app.route("/api/latest", methods=["GET"])
def get_latest_data():
    """获取最新数据"""
    if received_data:
        return jsonify(received_data[-1])
    else:
        return jsonify({"error": "No data received yet"}), 404


@app.route("/api/data/<int:index>", methods=["GET"])
def get_specific_data(index):
    """获取特定索引的数据"""
    if 1 <= index <= len(received_data):
        return jsonify(received_data[index - 1])
    else:
        return jsonify(
            {"error": f"Index {index} out of range (1-{len(received_data)})"}
        ), 404


@app.route("/api/clear", methods=["POST"])
def clear_data():
    """清空所有数据"""
    global received_data, received_count
    received_data.clear()
    received_count = 0
    return jsonify({"status": "success", "message": "All data cleared"})


@app.route("/api/list", methods=["GET"])
def get_data_list():
    """获取数据列表，支持分页和基本过滤"""
    try:
        page = int(request.args.get("page", 1))
        per_page = int(request.args.get("per_page", 20))
        label_filter = request.args.get("label", "")
        search_term = request.args.get("search", "")

        # 过滤数据
        filtered_data = received_data

        if label_filter:
            filtered_data = [
                d
                for d in filtered_data
                if "structured" in d and d["structured"].get("Label") == label_filter
            ]

        if search_term:
            filtered_data = [
                d
                for d in filtered_data
                if any(search_term.lower() in str(v).lower() for v in d.values())
                or (
                    "structured" in d
                    and any(
                        search_term.lower() in str(v).lower()
                        or search_term.lower() in k.lower()
                        for k, v in d["structured"].items()
                    )
                )
            ]

        # 分页
        total = len(filtered_data)
        start = (page - 1) * per_page
        end = start + per_page
        paginated_data = filtered_data[start:end]

        # 构建响应
        response_data = []
        for i, data in enumerate(paginated_data, start + 1):
            item = {
                "id": i,
                "timestamp": data.get("timestamp", ""),
                "has_structured": "structured" in data,
                "feature_count": len(data.get("structured", {})),
            }

            if "structured" in data:
                structured = data["structured"]
                item.update(
                    {
                        "label": structured.get("Label", ""),
                        "src_ip": structured.get("SrcIP", ""),
                        "dst_ip": structured.get("DstIP", ""),
                        "protocol": structured.get("Protocol", ""),
                        "flow_duration": structured.get("FlowDuration", 0),
                    }
                )

            response_data.append(item)

        return jsonify(
            {
                "status": "success",
                "total": total,
                "page": page,
                "per_page": per_page,
                "total_pages": (total + per_page - 1) // per_page,
                "data": response_data,
            }
        )
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 400


@app.route("/api/delete/<int:index>", methods=["DELETE"])
def delete_data(index):
    """删除特定索引的数据"""
    global received_data
    try:
        if 1 <= index <= len(received_data):
            deleted = received_data.pop(index - 1)
            return jsonify(
                {
                    "status": "success",
                    "message": f"Deleted data #{index}",
                    "deleted_data": deleted,
                }
            )
        else:
            return jsonify(
                {
                    "status": "error",
                    "message": f"Index {index} out of range (1-{len(received_data)})",
                }
            ), 404
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 400


@app.route("/api/export/csv", methods=["GET"])
def export_csv():
    """导出所有结构化数据为CSV"""
    try:
        if not received_data:
            return jsonify({"status": "error", "message": "No data to export"}), 404

        # 收集所有结构化数据
        structured_list = []
        for data in received_data:
            if "structured" in data:
                structured_list.append(data["structured"])

        if not structured_list:
            return jsonify(
                {"status": "error", "message": "No structured data to export"}
            ), 404

        # 获取所有可能的列
        all_keys = set()
        for item in structured_list:
            all_keys.update(item.keys())

        # 确保列顺序一致
        fieldnames = sorted(all_keys)

        # 创建CSV输出
        output = StringIO()
        writer = csv.DictWriter(output, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(structured_list)

        # 准备文件下载
        output.seek(0)
        timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
        filename = f"cicflowmeter_data_{timestamp}.csv"

        # 转换为字节流
        csv_bytes = BytesIO(output.getvalue().encode("utf-8-sig"))
        csv_bytes.seek(0)

        return send_file(
            csv_bytes,
            mimetype="text/csv",
            as_attachment=True,
            download_name=filename,
        )
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 400


@app.route("/api/export/json", methods=["GET"])
def export_json():
    """导出所有数据为JSON"""
    try:
        if not received_data:
            return jsonify({"status": "error", "message": "No data to export"}), 404

        timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
        filename = f"cicflowmeter_data_{timestamp}.json"

        # 转换为字节流
        json_str = json.dumps(received_data, indent=2, ensure_ascii=False)
        json_bytes = BytesIO(json_str.encode("utf-8"))
        json_bytes.seek(0)

        return send_file(
            json_bytes,
            mimetype="application/json",
            as_attachment=True,
            download_name=filename,
        )
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 400


@app.route("/api/stats", methods=["GET"])
def get_stats():
    """获取统计数据"""
    structured_count = sum(1 for d in received_data if "structured" in d)
    csv_only_count = len(received_data) - structured_count

    # 收集标签统计
    label_stats = {}
    for data in received_data:
        if "structured" in data:
            label = data["structured"].get("Label", "Unknown")
            label_stats[label] = label_stats.get(label, 0) + 1

    # 收集协议统计
    protocol_stats = {}
    for data in received_data:
        if "structured" in data:
            protocol = data["structured"].get("Protocol", "Unknown")
            protocol_stats[protocol] = protocol_stats.get(protocol, 0) + 1

    return jsonify(
        {
            "status": "success",
            "total_count": received_count,
            "in_memory_count": len(received_data),
            "structured_count": structured_count,
            "csv_only_count": csv_only_count,
            "label_stats": label_stats,
            "protocol_stats": protocol_stats,
            "timestamp": datetime.datetime.now().isoformat(),
        }
    )


@app.route("/health", methods=["GET"])
def health_check():
    """健康检查端点 - 监控API服务器状态"""
    import psutil
    import socket

    try:
        # 获取系统信息
        cpu_percent = psutil.cpu_percent(interval=0.1)
        memory = psutil.virtual_memory()
        disk = psutil.disk_usage("/")

        # 获取进程信息
        process = psutil.Process()
        process_memory = process.memory_info().rss / 1024 / 1024  # MB

        # 获取网络连接信息
        connections = len(process.connections())

        health_data = {
            "status": "healthy",
            "timestamp": datetime.datetime.now().isoformat(),
            "version": "1.0.0",
            "service": "CICFlowMeter API Server",
            "system": {
                "cpu_percent": cpu_percent,
                "memory_total_mb": memory.total / 1024 / 1024,
                "memory_used_mb": memory.used / 1024 / 1024,
                "memory_free_mb": memory.free / 1024 / 1024,
                "memory_percent": memory.percent,
                "disk_total_gb": disk.total / 1024 / 1024 / 1024,
                "disk_used_gb": disk.used / 1024 / 1024 / 1024,
                "disk_free_gb": disk.free / 1024 / 1024 / 1024,
                "disk_percent": disk.percent,
            },
            "process": {
                "pid": process.pid,
                "memory_mb": round(process_memory, 2),
                "cpu_percent": process.cpu_percent(interval=0.1),
                "connections": connections,
                "threads": process.num_threads(),
                "create_time": datetime.datetime.fromtimestamp(
                    process.create_time()
                ).isoformat(),
            },
            "api": {
                "total_requests": received_count,
                "data_in_memory": len(received_data),
                "structured_data": sum(1 for d in received_data if "structured" in d),
                "uptime_seconds": int(time.time() - process.create_time()),
                "endpoints": {
                    "predict": "POST /flows",
                    "web_ui": "GET /",
                    "health": "GET /health",
                    "stats": "GET /api/stats",
                    "export_csv": "GET /api/export/csv",
                    "export_json": "GET /api/export/json",
                },
            },
            "dependencies": {
                "flask": True,
                "psutil": True,
                "network_connectivity": True,
            },
        }

        return jsonify(health_data), 200

    except Exception as e:
        return jsonify(
            {
                "status": "unhealthy",
                "timestamp": datetime.datetime.now().isoformat(),
                "error": str(e),
                "message": "Health check failed",
            }
        ), 500


def print_summary():
    """定期打印接收统计"""
    while True:
        time.sleep(10)
        print(f"\n{'=' * 80}")
        print(f"📊 API接收统计 ({time.strftime('%Y-%m-%d %H:%M:%S')})")
        print(f"  • 总计接收: {received_count} 条数据")
        print(f"  • 内存中数据: {len(received_data)} 条")
        print(f"  • 数据保存目录: {SAVE_DIR}/")
        print(f"  • Web界面: http://127.0.0.1:5000/")

        if received_data:
            # 统计数据结构类型
            structured_count = sum(1 for d in received_data if "structured" in d)
            csv_only_count = len(received_data) - structured_count

            print(f"\n  数据结构统计:")
            print(
                f"    • 结构化数据: {structured_count} 条 ({structured_count / len(received_data) * 100:.1f}%)"
            )
            print(
                f"    • 纯CSV数据: {csv_only_count} 条 ({csv_only_count / len(received_data) * 100:.1f}%)"
            )

            if structured_count > 0:
                # 分析最新结构化数据的特征
                structured_data = [d for d in received_data if "structured" in d]
                if structured_data:
                    latest_structured = structured_data[-1]["structured"]
                    print(f"\n  最新结构化数据特征:")
                    print(f"    • 特征总数: {len(latest_structured)} 个")

                    # 统计不同类型特征
                    duration_features = [
                        k
                        for k in latest_structured.keys()
                        if "Duration" in k or "IAT" in k
                    ]
                    packet_features = [
                        k
                        for k in latest_structured.keys()
                        if "Packet" in k or "Pkt" in k
                    ]
                    length_features = [
                        k
                        for k in latest_structured.keys()
                        if "Length" in k or "Size" in k
                    ]

                    print(f"    • 时长特征: {len(duration_features)} 个")
                    print(f"    • 包特征: {len(packet_features)} 个")
                    print(f"    • 长度特征: {len(length_features)} 个")

        print(f"\n  可用端点:")
        print(f"    • GET  /          - Web界面查看数据")
        print(f"    • GET  /api/all   - 获取所有数据(JSON)")
        print(f"    • GET  /api/latest - 获取最新数据(JSON)")
        print(f"    • POST /api/clear - 清空所有数据")
        print(f"    • GET  /api/list  - 分页获取数据")
        print(f"    • GET  /api/stats - 获取统计数据")
        print(f"    • GET  /api/export/csv - 导出CSV")
        print(f"    • GET  /api/export/json - 导出JSON")
        print(f"{'=' * 80}")


def main():
    print(f"{'=' * 80}")
    print("🚀 启动CICFlowMeter API完整测试服务器 - 增强修复版")
    print(f"{'=' * 80}")
    print("\n📡 API端点:")
    print("  • POST /flows     - 接收CICFlowMeter发送的流量数据")
    print("  • GET  /           - Web界面查看所有接收的数据")
    print("  • GET  /api/all    - 获取所有数据(JSON格式)")
    print("  • GET  /api/latest - 获取最新一条数据(JSON格式)")
    print("  • GET  /api/data/N - 获取第N条数据(JSON格式)")
    print("  • GET  /api/list   - 分页获取数据(支持过滤)")
    print("  • GET  /api/stats  - 获取统计数据")
    print("  • POST /api/clear  - 清空所有数据")
    print("  • DELETE /api/delete/N - 删除第N条数据")
    print("  • GET  /api/export/csv - 导出CSV")
    print("  • GET  /api/export/json - 导出JSON")

    print(f"\n📂 数据保存:")
    print(f"  所有接收的数据会自动保存到: {SAVE_DIR}/")
    print(f"  文件名格式: data_0001.json, data_0002.json, ...")

    print(f"\n🌐 Web界面增强功能:")
    print(f"  1. 完整的数据表格展示，支持排序和分页")
    print(f"  2. 多种过滤条件：标签、协议、关键词搜索")
    print(f"  3. 数据详情查看：JSON格式化、特征分类")
    print(f"  4. 数据导出功能：CSV和JSON格式")
    print(f"  5. 实时统计信息：数据分布、标签统计")
    print(f"  6. 响应式设计：适配不同屏幕尺寸")

    print(f"\n⚙️ 配置注意:")
    print(f"  • CICFlowMeter的API URL应设置为: http://127.0.0.1:5000/flows")
    print(f"  • 如果端口被占用，可修改代码中的端口号")
    print(f"  • 数据格式: 结构化JSON + 原始CSV")

    print(f"\n🛑 停止服务器: 按Ctrl+C")
    print(f"{'=' * 80}\n")

    # 启动统计线程
    stats_thread = threading.Thread(target=print_summary, daemon=True)
    stats_thread.start()

    # 启动Flask服务器
    app.run(host="0.0.0.0", port=5000, debug=False)


if __name__ == "__main__":
    main()
