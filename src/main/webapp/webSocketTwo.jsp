<%@ page language="java" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html>
<head>
    <title>index Page</title>
</head>
<body>
    Welcome<br/><input id="text" type="text" value="0"/>
    <button onclick="send()">发送消息</button>
    <hr/>
    <button onclick="closeWebSocket()">关闭WebSocket连接</button>
    <hr/>
    <div id="message"></div>
    <table id="tb" class="altrowstable">
		<th align="center"  colspan="9">实时信息监控</th>
	</table>
</body>

<script type="text/javascript">
    var websocket = null;
    //判断当前浏览器是否支持WebSocket
    if ('WebSocket' in window) {
        websocket = new WebSocket("ws://localhost:8080/JavaWebSocket/websocketTwo?room=1&openId=a");
    }
    else {
        alert('当前浏览器 Not support websocket')
    }

    //连接发生错误的回调方法
    websocket.onerror = function () {
        console.log("WebSocket连接发生错误");
    };

    //连接成功建立的回调方法
    websocket.onopen = function (event) {
        console.log("WebSocket连接成功");
        console.log(event.data);
    }

    //接收到消息的回调方法
    websocket.onmessage = function (event) {
        setMessageInnerHTML(event.data);
    }

    //连接关闭的回调方法
    websocket.onclose = function () {
        console.log("WebSocket连接关闭");
    }

    //监听窗口关闭事件，当窗口关闭时，主动去关闭websocket连接，防止连接还没断开就关闭窗口，server端会抛异常。
    window.onbeforeunload = function () {
        closeWebSocket();
    }

    var users = [];
    var name = 0;
    //将消息显示在网页上
    function setMessageInnerHTML(data) {
        var jsonDate = JSON.parse(data);
        if(jsonDate.type==0){
            var names = jsonDate.names;
            for(var i=0;i<names.length;i++){
                var boolean = false;
                if(name == 0){
                    if(names[i].isNew){
                        name = names[i].name;
                        boolean = true;
                    }
                }
                var na = names[i].name;
                if(users.indexOf(na)<0){
                    users.push(na);
                    var row;
                    var table=document.getElementById("tb");
                    row=table.insertRow(1);
                    var row1 = row.insertCell(0);
                    row1.appendChild(document.createTextNode(na));
                    var row2 = row.insertCell(1);
                    var element = document.createElement('p');
                    element.id=na;
                    if(boolean){
                        element.style = "color:blue;";
                        boolean = false;
                    }
                    element.appendChild(document.createTextNode("0"));
                    row2.appendChild(element);
                }
            }
        }else if(jsonDate.type==1){
                document.getElementById(jsonDate.name).innerHTML=jsonDate.num;
        }
    }

    //关闭WebSocket连接
    function closeWebSocket() {
        websocket.close();
    }

    //发送消息
    function send() {
        websocket.send(name);
    }
</script>
</html>