# **$\color{red}{Camera开机自动连接WiFi}$**

------

**基本要求：** 使用一个脚本结合系统服务来实现，编写脚本来扫描并连接指定的WiFi热点，并设置该脚本在启动时自动运行。

- 开机扫描到该热点后，自动进行连接。
- 没扫描到该热点，则进行等待，一直扫描，直到该热点出现，然后进行连接。
- 第一次连接热点后，不管是否成功，等待5s，再进行一次连接。
- 2次尝试连接后，不再进行扫描和连接。
  
## 1、安装工具 network-manager（nmcli）

```ubuntu
sudo apt-get install network-manager
sudo apt install vim
```

## 2、创建连接脚本 AutoConnectWifi.sh

```ubuntu 
// 创建脚本文件
sudo touch AutoConnectWifi.sh
// 输入脚本文件内容
sudo vim AutoConnectWifi.sh
// 给予执行权限
sudo chmod +x AutoConnectWifi.sh
```

## 3、cron的@reboot指令

```ubuntu
// 设置EDITOR环境变量
export EDITOR=vim
// 编辑当前用户的crontab文件
crontab -e
// 文本中添加@reboot指令
@reboot /home/hit/AutoConnectWifi.sh
// 确认crontab已更新
crontab -l
// 测试@reboot任务，重启系统
sudo reboot
```

## 4、WiFi测试

```ubuntu
// 检查网络连接状态，查找无线设备 wlan0 等
nmcli device status
// 检查WiFi连接的详细信息
nmcli connection show
```