package cic.cs.unb.ca.ifm.ui;

import java.awt.Dimension;
import java.awt.Toolkit;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Component;

public class AboutDialog {

	protected static final Logger logger = LoggerFactory.getLogger(AboutDialog.class);
	
	public static final String CONTENT = new StringBuilder("Permission is hereby granted, free of charge, to any person obtaining a copy ")
			.append("of this software and associated documentation files (CICFlowMeter), to deal ")
			.append("in the Software without restriction, including without limitation the rights ")
			.append("to use, copy, modify, merge, publish, distribute, sublicense, and/or sell ")
			.append("copies of the Software, and to permit persons to whom the Software is ")
			.append("furnished to do so, subject to the following conditions: ")
			.append("\n\n")
			.append("The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.")
			.append("\n\n")
			.append("THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR ")
			.append("IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, ")
			.append("FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE ")
			.append("AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER ")
			.append("LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, ")
			.append("OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.")
			.toString();
	
	
    public static void main(String[] args){
    	show(null);
    }
	
    public static void show(JFrame parent) {
    	
    	
    	ImageIcon icon;
    	if(parent !=null) {
    		//URL url = parent.getClass().getClassLoader().getResource("cicaboutlogo.png");
    		//logger.info("logopath: {}",url.getPath());
    		//icon =  new ImageIcon(url.getPath());
    		icon = new ImageIcon(Toolkit.getDefaultToolkit().getImage(parent.getClass().getClassLoader().getResource("cicaboutlogo.png")));
    	}else {
    		String rootPath = System.getProperty("user.dir");
    		icon = new ImageIcon(rootPath+"/src/main/resources/cicaboutlogo.png");
    	}
		
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        Box baseBox = Box.createVerticalBox();
        
        JLabel lblCpr = new JLabel("Copyright (c) 2017 Canadian Institute for Cybersecurity (CIC)");
        lblCpr.setAlignmentX(Component.CENTER_ALIGNMENT);
        baseBox.add(lblCpr);
        baseBox.add(Box.createVerticalStrut(5));
        
        String implVersion = null;
        Package pkg = AboutDialog.class.getPackage();
        if (pkg != null) {
            implVersion = pkg.getImplementationVersion();
        }
        JLabel lblVersion = new JLabel("Version: " + (implVersion != null ? implVersion : "4.1-enhanced"));
        lblVersion.setForeground(Color.GRAY);
        lblVersion.setAlignmentX(Component.CENTER_ALIGNMENT);
        baseBox.add(lblVersion);
        baseBox.add(Box.createVerticalStrut(5));
        
        // 增强版功能描述
        JLabel lblEnhanced = new JLabel("<html><center>增强版功能：时间规则标签系统、API集成、分组管理<br>实时流量特征提取与自动标注</center></html>");
        lblEnhanced.setForeground(new Color(0, 102, 0)); // 深绿色
        lblEnhanced.setAlignmentX(Component.CENTER_ALIGNMENT);
        baseBox.add(lblEnhanced);
        baseBox.add(Box.createVerticalStrut(15));
        
        JTextArea txtArea = new JTextArea();
        txtArea.setFont(UIManager.getFont("OptionPane.font"));
        txtArea.setEditable(false);
        txtArea.setLineWrap(true);
        txtArea.setWrapStyleWord(true);
        txtArea.append(CONTENT);
        baseBox.add(txtArea);
        
        JLabel lblLink = new JLabel("http://www.unb.ca/cic/research/applications.html");
        lblLink.setForeground(Color.BLUE);
        lblLink.setAlignmentX(Component.CENTER_ALIGNMENT);
        baseBox.add(Box.createVerticalStrut(10));
        baseBox.add(lblLink);
        baseBox.add(Box.createVerticalStrut(10));
        
        panel.add(baseBox);
        
        UIManager.put("OptionPane.minimumSize",new Dimension(800, 450));
        JOptionPane.showMessageDialog(parent, panel, "About CICFlowMeter (增强版)", JOptionPane.PLAIN_MESSAGE, icon);
    }
}
