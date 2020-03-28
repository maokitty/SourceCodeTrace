package paxi.maokitty.source.thrift;

import paxi.maokitty.source.annotation.*;
import paxi.maokitty.source.util.Code;

/**
 * Created by maokitty on 2019/8/15.
 */
@Background(
        target = "了解thrift在client和server请求过程中，TBinaryProtocol的处理方式",
        conclusion = "client会按照字节的写入规则严格的写入和读取。底层通信实际上就是socket,服务端接收到请求后，交由对应用户的实现接口来调用实现类，再将结果写入输出流， 客户端等结果返回后再按照规则读取结果,完成1次rpc的调用",
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
        Code.SLICE.source(" //1:网路请求相关设置" +
                "            transport=new TSocket(\"127.0.0.1\",9000,1000);" +
                "            //2:传输数据的编码方式" +
                "            TProtocol protocol=new TBinaryProtocol(transport);" +
                "            //3:建立连接" +
                "            transport.open();" +
                "            //4:创建客户端" +
                "            DemoService.Client client=new DemoService.Client(protocol);" +
                "            //5:发起请求" +
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
        Code.SLICE.source(" send_say(msg);" +
                    "       return recv_say();")
                .interpretation("这段代码是经过编译自动形成的")
        .interpretation("send_say的核心是 sendBase,recv_say的核心则是receiveBase，receiveBase会根据返回的最终结果，决定是成功返回值还是抛出异常");
    }

    @Trace(
            index = 2,
            originClassName = "org.apache.thrift.TServiceClient",
            function = "protected void sendBase(String methodName, TBase args) throws TException"
    )
    @KeyPoint
    public void sendBase(){
        Code.SLICE.source("oprot_.writeMessageBegin(new TMessage(methodName, TMessageType.CALL, ++seqid_));")
                .interpretation("opprot即初始化 DemoService.client 时传入的 TBinaryProtocol ,seqid默认值为0")
                .interpretation("Begin部分的写入首先是按照字节数写入版本、然后是方法名的长度，再是方法名，最后写入序列号，按照特定的规则写入数据");
        Code.SLICE.source("args.write(oprot_);")
                .interpretation("负责将参数写入Buffer,它会按照参数的顺序写入，每个参数又是按照类型、序号、值的顺序写入");
        //..
        Code.SLICE.source("oprot_.getTransport().flush();")
                .interpretation("数据已经写入了缓冲区，把没有写完的数据写入对应的文件描述符");

    }

    @Trace(
            index = 3,
            originClassName = "org.apache.thrift.protocol.TBinaryProtocol",
            function = "public void writeMessageBegin(TMessage message) throws TException "
    )
    public void writeMessageBegin(){
        //..
        Code.SLICE.source(" int version = VERSION_1 | message.type;" +
                "      writeI32(version);" +
                "      writeString(message.name);" +
                "      writeI32(message.seqid);")
                .interpretation("默认使用的是严格写的模式，thrift先写入版本信息，版本信息本身会蕴含方法的类型(如方法调用或者是返回)，然后写入方法的名字和序号");
    }
    @Trace(
            index = 4,
            originClassName = "org.apache.thrift.protocol.TBinaryProtocol",
            function = "public void writeI32(int i32) throws TException"
    )
    public void writeI32(){
        Code.SLICE.source("i32out[0] = (byte)(0xff & (i32 >> 24));")
                .interpretation("将传入的子4字节取对应的每个字节的位置，放入一个4字节的数组中");
        //...
        Code.SLICE.source("trans_.write(i32out, 0, 4);")
                .interpretation("trans即初始化时传入的transport,对于TSocket而言写的实现就是在TIOStreamTransport中");
    }
    @Trace(
            index = 5,
            originClassName = "org.apache.thrift.transport.TIOStreamTransport",
            function = "public void write(byte[] buf, int off, int len) throws TTransportException"
    )
    public void write(){
       //..
        Code.SLICE.source("outputStream_.write(buf, off, len);")
                .interpretation("outputstream即socket中添加了buffer的输出流,数据写入SocketOutputStream");
    }
    @Trace(
            index = 6,
            originClassName = "org.apache.thrift.protocol.TBinaryProtocol",
            function = "public void writeString(String str) throws TException "
    )
    public void writeString(){
        //..
        Code.SLICE.source("  byte[] dat = str.getBytes(\"UTF-8\");" +
                "      writeI32(dat.length);" +
                "      trans_.write(dat, 0, dat.length);")
                .interpretation("先是写入了字符串的长度，然后再把整个字符串写入输出流");
    }

    @Trace(
            index = 7,
            originClassName = "paxi.maokitty.verify.service.DemoService.say_args.say_argsStandardScheme",
            function = "public void write(org.apache.thrift.protocol.TProtocol oprot, say_args struct) throws org.apache.thrift.TException "
    )
    public void sayArgsWrite(){
        //...
        Code.SLICE.source(" if (struct.msg != null) {" +
                "          oprot.writeFieldBegin(MSG_FIELD_DESC);" +
                "          oprot.writeString(struct.msg);" +
                "          oprot.writeFieldEnd();" +
                "        }")
                .interpretation("msg字段不为null开始写入")
                .interpretation("1:依次写入msg字段的类型，和ID，对于msg字段来讲就是string,详细的类型可以看 类 TType")
                .interpretation("2:写入msg字段值本身的长度和值")
                .interpretation("3:End对于TBinaryProtocol来说什么都没有写入");
        //...
        Code.SLICE.source("oprot.writeFieldStop();")
                .interpretation("表明字段写入结束，会在结尾写入一字节0");
    }

    @Trace(
            index = 8,
            originClassName = "paxi.maokitty.verify.MySayServer",
            function = "public static void main(String[] args)",
            more = "thrift项目下的子module thriftServer的main函数"
    )
    public void serverMain(){
        Code.SLICE.source(" //1:创建等待连接的serverSocket" +
                "            TServerSocket serverSocket=new TServerSocket(9000);" +
                "            //2:构建server所需要的参数" +
                "            TServer.Args serverArgs=new TServer.Args(serverSocket);" +
                "            //3:逻辑处理" +
                "            TProcessor processor=new DemoService.Processor<DemoService.Iface>(new DemoServiceImpl());" +
                "            //4:解析协议" +
                "            serverArgs.protocolFactory(new TBinaryProtocol.Factory());" +
                "            serverArgs.processor(processor);" +
                "            //5:组织组件完成功能" +
                "            TServer server=new TSimpleServer(serverArgs);" +
                "            server.serve();")
                .interpretation("server表明接收连接的接口，表明处理数据的protocol以及收到连接后的处理器，然后启动Server等待连接的到来");
    }

    @Trace(
            index = 9,
            originClassName = "org.apache.thrift.server.TSimpleServer",
            function = "public void serve() "
    )
    public void serve(){
       //...
        Code.SLICE.source("client = serverTransport_.accept();")
                .interpretation("底层就是ServerSocket的accept函数，它将返回的结果封装成TSocket返回");
        //..
        Code.SLICE.source(" processor = processorFactory_.getProcessor(client);" +
                "          inputTransport = inputTransportFactory_.getTransport(client);" +
                "          outputTransport = outputTransportFactory_.getTransport(client);" +
                "          inputProtocol = inputProtocolFactory_.getProtocol(inputTransport);" +
                "          outputProtocol = outputProtocolFactory_.getProtocol(outputTransport);" +
                "          while (processor.process(inputProtocol, outputProtocol)) {}")
                .interpretation("processor即thrift根据用户写的代码实现类的processor,其余四个参数则是得到的请求中获取的协议处理器，用来读取数据和返回数据,拿到后交由处理器处理");
    }
    @Trace(
            index = 10,
            originClassName = "org.apache.thrift.TBaseProcessor",
            function = "public boolean process(TProtocol in, TProtocol out) throws TException"
    )
    @KeyPoint
    public void process(){
        Code.SLICE.source("TMessage msg = in.readMessageBegin();")
                .interpretation("获取方法名相关信息");
        Code.SLICE.source("ProcessFunction fn = processMap.get(msg.name);")
                .interpretation("在服务端的Processor初始化的时候，就会把所有的函数名都放在内存里面，然后读取,这里就是查找函数名");
        Code.SLICE.source("if (fn == null) {" +
                "      TProtocolUtil.skip(in, TType.STRUCT);" +
                "      in.readMessageEnd();" +
                "      TApplicationException x = new TApplicationException(TApplicationException.UNKNOWN_METHOD, \"Invalid method name: '\"+msg.name+\"'\");" +
                "      out.writeMessageBegin(new TMessage(msg.name, TMessageType.EXCEPTION, msg.seqid));" +
                "      x.write(out);" +
                "      out.writeMessageEnd();" +
                "      out.getTransport().flush();" +
                "      return true;" +
                "    }")
                .interpretation("如果没有这个函数，也就是说客户端是有这个方法的，但是服务端没有，这个时候，就返回异常");
        Code.SLICE.source("fn.process(msg.seqid, in, out, iface);")
                .interpretation("找到了对应的处理函数，交由它来处理");
    }

    @Trace(
            index = 11,
            originClassName = "org.apache.thrift.protocol.TBinaryProtocol",
            function = "public TMessage readMessageBegin() throws TException "
    )
    public void readMessageBegin(){
        Code.SLICE.source("int size = readI32();")
                .interpretation("先读取数据中的前32字节,从输入的过程可以看到它就是方法的版本");
        //...
        Code.SLICE.source("return new TMessage(readString(), (byte)(size & 0x000000ff), readI32());")
                .interpretation("读到的版本没有问题，依次读取方法名本身,序列号");
    }
    @Trace(
            index = 12,
            originClassName = "org.apache.thrift.ProcessFunction",
            function = "public final void process(int seqid, TProtocol iprot, TProtocol oprot, I iface) throws TException"
    )
    public void process0(){
        Code.SLICE.source("T args = getEmptyArgsInstance();")
             .interpretation("拿到参数的类型，这里就是 say_args");
        //..
        Code.SLICE.source("args.read(iprot);")
                .interpretation("从say_args的scheme（say_argsStandardScheme）中读取参数");
        //..
        Code.SLICE.source("TBase result = getResult(iface, args);")
                .interpretation("调用实现类，去执行用户自己写的逻辑，并得到对应的结果");
        //...
        Code.SLICE.source("oprot.writeMessageBegin(new TMessage(getMethodName(), TMessageType.REPLY, seqid));" +
                " result.write(oprot);" +
                "    oprot.writeMessageEnd();" +
                "    oprot.getTransport().flush();")
                .interpretation("开始往返回Stream中写入数据，表明这是对那个方法的返回值，然后写入返回的结果，最后输入socket");
    }
    @Trace(
            index = 13,
            originClassName = "paxi.maokitty.verify.service.DemoService.say_args.say_argsStandardScheme",
            function = "public void read(org.apache.thrift.protocol.TProtocol iprot, say_args struct) throws org.apache.thrift.TException  "
    )
    public void readClientArgs(){
      //...
        Code.SLICE.source("schemeField = iprot.readFieldBegin();")
                .interpretation("读取字段，和写入顺序意义对应，也是先读到类型，然后是序号，当然这里也有可能读到结束符");
        Code.SLICE.source("if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { " +
                "            break;" +
                "          }")
                .interpretation("如果读到的是结束符，说明读取参数字段结束");
        Code.SLICE.source("switch (schemeField.id) {" +
                "            case 1: // MSG" +
                "              if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {" +
                "                struct.msg = iprot.readString();" +
                "                struct.setMsgIsSet(true);" +
                "              } else { " +
                "                org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);" +
                "              }" +
                "              break;" +
                "            default:" +
                "              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);" +
                "          }")
                .interpretation("根据读取到的序号，来按照序号处理字段，直到读取所有字段结束。这里可以看到两点")
                .interpretation("1:参数的序号对于thrift来说特别重要")
                .interpretation("2:如果这个字段不是服务端已有的字段，直接跳过对应的字节，提升容错性");
         //...
    }

    @Trace(
            index = 14,
            originClassName = "org.apache.thrift.TServiceClient",
            function = "protected void receiveBase(TBase result, String methodName) throws TException ",
            more = "客户端读消息"
    )
    @Recall(traceIndex = 11,tip = "都是去取方法")
    public void receiveBase(){
        Code.SLICE.source("TMessage msg = iprot_.readMessageBegin();")
                .interpretation("从SocketInputStream中读取内容，读的方法和服务端读取方法一模一样");
        Code.SLICE.source("if (msg.type == TMessageType.EXCEPTION) {" +
                "      TApplicationException x = TApplicationException.read(iprot_);" +
                "      iprot_.readMessageEnd();" +
                "      throw x;" +
                "    }")
                .interpretation("如果返回结果的类型是异常，则按照异常来处理，并抛出它");
        //..
        Code.SLICE.source("result.read(iprot_);")
                .interpretation("从say_result中读取结果");
        //...
    }
    @Trace(
            index = 15,
            originClassName = "paxi.maokitty.verify.service.DemoService.say_result.say_resultStandardScheme",
            function = "public void read(org.apache.thrift.protocol.TProtocol iprot, say_result struct) throws org.apache.thrift.TException "
    )
    public void readResult(){
        Code.SLICE.source(" schemeField = iprot.readFieldBegin();" +
                "          if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { " +
                "            break;" +
                "          }" +
                "          switch (schemeField.id) {" +
                "            case 0: // SUCCESS" +
                "              if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {" +
                "                struct.success = iprot.readString();" +
                "                struct.setSuccessIsSet(true);" +
                "              } else { " +
                "                org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);" +
                "              }" +
                "              break;" +
                "            case 1: // E" +
                "              if (schemeField.type == org.apache.thrift.protocol.TType.STRUCT) {" +
                "                struct.e = new paxi.maokitty.verify.exception.myException();" +
                "                struct.e.read(iprot);" +
                "                struct.setEIsSet(true);" +
                "              } else { " +
                "                org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);" +
                "              }" +
                "              break;" +
                "            default:" +
                "              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);" +
                "          }")
                .interpretation("读取的过程类似参数读取，只不过这里的读取方式是换成了结果的字段，同时对于无法识别的类型也会直接跳过");
    }












































}
