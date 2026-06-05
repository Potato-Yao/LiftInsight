= 开发过程记录

开发过程全部记录于项目目录下 doc/log.md。本章节选了部分内容。

== 节选内容

=== 11.29 下午

让 ChatGPT 生成了个 wrapper 封装了 LibreHardwareMonitorLib，但是返回的数据没有 CPU 温度和功率，GitHub 上查了查 issue，说是因为管理员权限的问题，用管理员就能出电压了，但是也不出温度。

又查了下，有些数据被华硕主板设置为不可访问，我需要研究研究 g-helper 是怎么读到它们的。另外拯救者也有类似的设计，看来需要对一些牌子做额外适配。

不对啊，在 LibreHardwareMonitor 的 UI 里是能看到 CPU 温度和功率的，显然是封装有问题。让 AI 也没调好，看库的 C# 代码也没看明白人家是怎么调用的，怪了。

GitHub 上找到一个人做了 LibreHardwareMonitor 的封装，但是他怎么也不写文档，没看明白怎么用。

不过风扇转速这样的东西确实是是被华硕藏起来的，必须得研究研究 g-helper。

你们为什么不写文档啊？？？

=== 12.4 晚上

HardwareInfoManager 的 constructor 和 update() 里都需要处理硬件信息。同一个索引在这两个地方对应的名字是不一样的，假如手动写一一对应的代码没什么技术含量但维护负担太大。

所以我起初考虑写一个 Map，然后用反射。但是反射遍历起来未免也太慢了，update() 最好是 x ms 级的。

所以最后干脆用 Python 写了个脚本，resources/sensor_map.py，生成 Java 代码。只需要维护脚本里那个表就行了。现在 constructor 和 update() 里巨长的 if-else 就是用它生成的，乐。

另外就是 CPU、GPU 这些类里成员变量很多，起初想用 lombok 简化 Getter 和 Setter，但是不知道为什么过不了编译，干脆就用 IDEA 生成了一堆 Getter 和 Setter，以后再折腾这个。

写了个 test，现在的 update() 平均需要跑 2 ms，感觉还有优化空间，虽然完全够用了。

=== 12.10 凌晨

今天在写磁盘和注册表相关的东西。需要用到 diskpart，是用 process 的 BufferedWriter 和 BufferedReader 交互的。这东西不太聪明，最开始的实现会卡住，认真看了这俩的文档发现我的用法有问题。后来明明已经 consume 了最开始的几行但是它仍旧会被读到。于是尝试了一些小妙招但是要么又卡住，要么返回空字符串，不会解决了。

想着一个 char 一个 char 读来 debug，没想到这样就能得到正确结果了，莫名其妙解决问题了。

值得一提的是，这个过程中我一直在问 Sonnet 4.5，但是它给的方法并无什么用。让它 debug 十分钟不如我自己写几个 println 来 debug 两分钟。感觉是我没有掌握正确用法导致的，不然这也太菜了。

=== 12.16 上午

今天想把 battery 的实现写出来，发现 wrapper 的实现很神经：当插电并在充电的时候，电池电流叫 Charged Current，在放电的时候，叫 Discharged Current，在插电，并且满电的时候叫 Charged/discharged Current。就说是 Hardware list 是随着插电状态更新的，但是我只在程序最开始的时候读一次 hardware list。这就导致我没法得知程序初始化后到底有没有在充电了。

花了不少时间研究能不能在不刷新 hardware list 的情况下知道到底读的是它们仨的谁，但是没想到。

不过我知道电脑充电的时候电流比充满时的大，因为充电时不仅有供电脑运行的电流，还有充电电流。又因为在插着电的时候电池是不放电的，所以此时要是有电流，那么就是充电电流，就说明电池没有充满。这样就得到一个判断方法：capacity 下降就是放电，上升就是充电，current = 0 就是充满，因为充电、放电都体现在这个 current 上。不过处于未知原因有时候 current 读不对，因此实际程序用 rate，充放电功率，来代替了。

=== 12.19 凌晨

回看日志，12.4 那天晚上我写了一个生成代码用的 Python 脚本。当时图省事直接用的元组列表，现在报应来了。

元组中有一项，假设是 A，用来生成类似于 cpu.A(lhmHelper.getValue(index[5])); 的代码，但是我现在需要的是 cpu.A(index[5]);，这就没办法了。所以我只好把 A 写成 A(index[5]); //，这样生成的代码就是 cpu.A(index[5]); //(lhmHelper.getValue(index[5]));，就能正常用了，跟 SQL 注入似的。