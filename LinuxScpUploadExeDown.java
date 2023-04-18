package com.garck.rwap;

import com.jcraft.jsch.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.Iterator;

public class LinuxScpUploadExeDown {
    private static  String ipAddress;  // Linux服务器用户名
    private static  int port;       // Linux服务器密码
    private static  String userName;   // Linux服务器主机名
    private static  String passWord;      // Linux服务器主机端口

    private static String local_file_path  ;       // 待上传的本地文件路径
    private static String removte_file_path = "/tmp/" + local_file_path;  // 在Linux服务器上保存的路径


    //主函数
    public static void main(String[] args) {
        System.out.println("欢迎使用Linux基线批量执行工具........");
        System.out.println("\n");

        getFileLinx(args);
        try {
            // Load Excel file
            File file = new File("target.xlsx");
            FileInputStream fis = new FileInputStream(file);
            Workbook workbook = new XSSFWorkbook(fis);
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            //跳过第一行
            rowIterator.next();
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();

                // Get IP
                Cell cellIP = row.getCell(0);
                String ipAddress = cellIP.getStringCellValue();

                // Get Port
                Cell cellPort= row.getCell(1);
                String strport;
                if (cellPort.getCellType() == CellType.STRING) {
                    strport = cellPort.getStringCellValue();
                } else if (cellPort.getCellType() == CellType.NUMERIC) {
                    strport = String.valueOf((int)cellPort.getNumericCellValue());
                } else {
                    strport = "";
                }
                //把String的端口转换给int
                port = Integer.parseInt(strport);

                // Get Username
                Cell cellUsername= row.getCell(2);
                String userName = cellUsername.getStringCellValue();

                // Get Password
                Cell cellPassword= row.getCell(3);
                String passWord;
                if (cellPassword.getCellType() == CellType.STRING) {
                    passWord = cellPassword.getStringCellValue();
                } else if (cellPassword.getCellType() == CellType.NUMERIC) {
                    passWord = String.valueOf((int)cellPassword.getNumericCellValue());
                } else {
                    passWord = "";
                }
                //test1(ipAddress,port,userName,passWord);
                try {
                    ConnectSSH(ipAddress,port,userName,passWord);
                } catch (Exception e) {
                    // 捕获并处理异常，//不打印异常信息
                    //e.printStackTrace();

                }
                System.out.println("\n");
            }
            workbook.close();
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("执行完毕！");
    }

    //从终端获取指定的文件
    public static void getFileLinx(String[] args){
        {
            // 遍历所有参数，查找-f参数后面的文件名
            String fileName = null;
            for (int i = 0; i < args.length; i++) {
                if ("-f".equals(args[i]) && i < args.length - 1) {
                    fileName = args[i+1];
                    break;
                }
            }

            // 如果找到了文件名，打开并输出
            if (fileName != null) {
                File file = new File(fileName);
                if (file.exists() && file.isFile()) {
                    local_file_path = fileName;
                    System.out.println("已选择基线脚本：" + fileName);
                } else {
                    System.out.println("请选择基线脚本");
                }
            } else {
                System.out.println("未找到文件名参数，请使用'-f'参数指定基线脚本");
            }
        }
    }

    //连接服务器的实现函数
    public static void ConnectSSH(String ipAddress,int port,String userName,String passWord){

        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(userName, ipAddress, port);
            session.setPassword(passWord);

            // 安全性考虑，不要使用ssh的公钥，而是获取用户输入的一些信息进行 ssh 验证
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            //创建一个session连接
            try {
                session.connect();
            } catch (JSchException e) {
                // 捕获并处理异常
                //不打印异常信息
                //e.printStackTrace();
                System.out.println(ipAddress+"：账号密码错误或策略不通");
            }

            //调用上传函数进行上传文件
            upLoadFile(session,ipAddress);

            //调用执行命令函数，进行执行命令，添加权限，执行脚本
            ExeScript(session,ipAddress);

            //调用下载文件函数，进行下载结果到本地，并且重命名
            try {
                Thread.sleep(9500); // 线程休眠9.5秒
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            downLoadFile(session,ipAddress);

            //调用清除函数，把本次过程产生的文件进行删除掉。
            clearFile(session,ipAddress);

            session.disconnect();
        } catch (JSchException e) {
            // e.printStackTrace();
        }
    }
    //上传脚本的实现函数
    private static void upLoadFile(Session session,String ipAddress){
        Channel channel = null;
        try {
            channel = session.openChannel("sftp");
        } catch (JSchException e) {
            //e.printStackTrace();
            System.out.println(ipAddress+"：连接未成功.....");
        }
        try {
            channel.connect();
        } catch (JSchException e) {

            e.printStackTrace();
        }
        ChannelSftp sftpChannel = (ChannelSftp) channel;
        try {
            sftpChannel.put(local_file_path, removte_file_path);
        } catch (SftpException e) {
            e.printStackTrace();
        }
        System.out.println(ipAddress+"：上传成功！");
        sftpChannel.exit();
    }
    //执行脚本的实现函数
    public static void ExeScript(Session session,String ipAddress){
        System.out.println(ipAddress+"：开始执行基线脚本");
        ChannelExec execChannel = null;
        try {
            execChannel = (ChannelExec) session.openChannel("exec");
        } catch (JSchException e) {
            e.printStackTrace();
        }
        execChannel.setCommand("chmod +x " + removte_file_path + "; cd /tmp ;" + removte_file_path);
        try {
            execChannel.connect();
        } catch (JSchException e) {
            e.printStackTrace();
        }
        // 断开连接
        execChannel.disconnect();
    }
    //下载文件的实现函数
    public static void downLoadFile(Session session,String ipAddress){
        System.out.println(ipAddress+"：执行完毕");
        Channel channel = null;
        try {
            channel = session.openChannel("sftp");
        } catch (JSchException e) {
            e.printStackTrace();
        }
        try {
            channel.connect();
        } catch (JSchException e) {
            e.printStackTrace();
        }
        ChannelSftp sftpChannel = (ChannelSftp) channel;

        System.out.println(ipAddress+"：开始下载文件");

        try {
            sftpChannel.get("/tmp/out.txt", ipAddress +"_out.txt");
        } catch (SftpException e) {
            e.printStackTrace();
        }
        System.out.println(ipAddress+"：下载成功");
        sftpChannel.exit();
    }
    //清除文件的实现函数
    public static void clearFile(Session session,String ipAddress){
        System.out.println(ipAddress+"：开始清除遗留文件");
        ChannelExec execChannel = null;
        try {
            execChannel = (ChannelExec) session.openChannel("exec");
        } catch (JSchException e) {
            e.printStackTrace();
        }
        execChannel.setCommand("rm -rf /tmp/out.txt " + removte_file_path);
        try {
            execChannel.connect();
        } catch (JSchException e) {
            e.printStackTrace();
        }
        System.out.println(ipAddress+"：清除完毕");
        // 断开连接
        execChannel.disconnect();
    }
}

