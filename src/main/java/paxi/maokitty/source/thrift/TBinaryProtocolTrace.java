package paxi.maokitty.source.thrift;

import paxi.maokitty.source.annotation.Background;
import paxi.maokitty.source.annotation.Main;
import paxi.maokitty.source.annotation.Trace;
import paxi.maokitty.source.util.Code;

/**
 * Created by maokitty on 2019/8/15.
 */
@Background(
        target = "了解thrift在client和server请求过程中，TBinaryProtocol的处理方式",
        conclusion = "",
        sourceCodeProjectName = "thrift",
        sourceCodeAddress = "https://github.com/maokitty/javaVerify/tree/master/thrift",
        projectVersion = "1.0-SNAPSHOT"
)
public class TBinaryProtocolTrace {
    @Main
    @Trace(
            index = 0,
            originClassName = "paxi.maokitty.verify.App",
            function = "public static void main(String[] args)",
            more = "thrift项目下的子module thriftClient的main函数"
    )
    public void main(){
        //...
        Code.SLICE.source(" //1:网路请求相关设置\n" +
                "            transport=new TSocket(\"127.0.0.1\",9000,1000);\n" +
                "            //2:传输数据的编码方式\n" +
                "            TProtocol protocol=new TBinaryProtocol(transport);\n" +
                "            //3:建立连接\n" +
                "            transport.open();\n" +
                "            //4:创建客户端\n" +
                "            DemoService.Client client=new DemoService.Client(protocol);\n" +
                "            //5:发起请求\n" +
                "            String say = client.say(\"i am client\");")
                .interpretation("client发起请求的方式，下面会从client.say为入口去看，client的实现来自 DemoService.Client");
        //...
    }

    @Trace(
            index = 1,
            originClassName = "paxi.maokitty.verify.service.DemoService.Client",
            function = "public String say(String msg) throws paxi.maokitty.verify.exception.myException, org.apache.thrift.TException"
    )
    public void say(){
        Code.SLICE.source(" send_say(msg);\n" +
                    "       return recv_say();")
                .interpretation("这段代码是经过编译自动形成的")
        .interpretation("send_say的核心是 sendBase,recv_say的核心则是receiveBase");
    }

    @Trace(
            index = 2,
            originClassName = "org.apache.thrift.TServiceClient",
            function = "protected void sendBase(String methodName, TBase args) throws TException"
    )

    public void sendBase(){
        Code.SLICE.source("oprot_.writeMessageBegin(new TMessage(methodName, TMessageType.CALL, ++seqid_));")
                .interpretation("opprot即初始化 DemoService.client 时传入的 TBinaryProtocol ,seqid默认值为0");
    }

    @Trace(
            index = 3,
            originClassName = "org.apache.thrift.protocol.TBinaryProtocol",
            function = "public void writeMessageBegin(TMessage message) throws TException "
    )
    public void writeMessageBegin(){

    }









}
