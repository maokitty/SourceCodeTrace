package paxi.maokitty.source.redis;

import paxi.maokitty.source.annotation.Background;
import paxi.maokitty.source.annotation.KeyPoint;
import paxi.maokitty.source.annotation.Main;
import paxi.maokitty.source.annotation.Trace;
import paxi.maokitty.source.util.Code;

/**
 * Created by maokitty on 19/6/23.
 */
@Background(
        target = "了解用户调用了set命令之后，redis执行的过程，以及对 string value的一些定义",
        conclusion = "在读到set命令之后，对于传进来的数据会转换成redisObject,而根据string value长度的不同使用不同的编码，同时存储的结构也会不一样，以达到优化内存的目的",
        sourceCodeProjectName = "redis",
        sourceCodeAddress = "https://github.com/antirez/redis",
        projectVersion = "5.0.0"
)
public class SetCommandExecuteTrace {
    @Main
    @Trace(
            index = 0,
            originClassName = "networking.c",
            function = "void processInputBuffer(client *c) ",
            introduction = "从缓存中读取数据，读到的内容就是一条完整的命令,在这之前，会有监听连接建立，监听已经建立链接时间的触发，一但连接有事件触发，将读到的内容放入buffer，这里开始读取内容"
    )
    public void processInputBuffer(){
        //...
        Code.SLICE.source("if (processInlineBuffer(c) != C_OK) break;")
                .interpretation("解析命令的结构");
        //...
        Code.SLICE.source("if (processCommand(c) == C_OK)")
                .interpretation("执行命令");

    }

    @Trace(
            index = 1,
            originClassName = "networking.c",
            function = "int processInlineBuffer(client *c)"
    )
    public void processInlineBuffer(){
        //...
        Code.SLICE.source(" for (c->argc = 0, j = 0; j < argc; j++) {\n" +
                "        if (sdslen(argv[j])) {\n" +
                "            c->argv[c->argc] = createObject(OBJ_STRING,argv[j]);\n" +
                "            c->argc++;\n" +
                "        } else {\n" +
                "            sdsfree(argv[j]);\n" +
                "        }\n" +
                "    }")
                .interpretation("对于读到的每一个参数，都会创建一个object放入到 argv 中");
    }

    @Trace(
            index = 2,
            originClassName = "networking.c",
            function = "robj *createObject(int type, void *ptr)"
    )
    public void createObject(){
        Code.SLICE.source(" robj *o = zmalloc(sizeof(*o));\n" +
                "    o->type = type;\n" +
                "    o->encoding = OBJ_ENCODING_RAW;\n" +
                "    o->ptr = ptr;\n" +
                "    o->refcount = 1;\n" +
                "\n" +
                "    /* Set the LRU to the current lruclock (minutes resolution), or\n" +
                "     * alternatively the LFU counter. */\n" +
                "    if (server.maxmemory_policy & MAXMEMORY_FLAG_LFU) {\n" +
                "        o->lru = (LFUGetTimeInMinutes()<<8) | LFU_INIT_VAL;\n" +
                "    } else {\n" +
                "        o->lru = LRU_CLOCK();\n" +
                "    }\n" +
                "    return o;\n")
                .interpretation("创建 redisObject 对象，创建默认指定 encoding 为 OBJ_ENCODING_RAW");
    }
    @Trace(
            index = 3,
            originClassName = "server.h",
            function = "struct define of redisObject "
    )
    public void structDefineOfRedisObject(){
        Code.SLICE.source("typedef struct redisObject {\n" +
                "    unsigned type:4;\n" +
                "    unsigned encoding:4;\n" +
                "    unsigned lru:LRU_BITS; /* LRU time (relative to global lru_clock) or\n" +
                "                            * LFU data (least significant 8 bits frequency\n" +
                "                            * and most significant 16 bits access time). */\n" +
                "    int refcount;\n" +
                "    void *ptr;\n" +
                "} robj;")
                .interpretation("redisObject的定义")
                .interpretation("1:type 占4bit,指string/list/hash/zset/set")
                .interpretation("2:encoding 占4bit,指具体的编码方式")
                .interpretation("3:lru时间戳，LRU_BITS 取值为 24bit")
                .interpretation("4:refcont引用次数， 最多占4字节")
                .interpretation("5:数据指针，最多8字节")
                ;
    }




    @Trace(
            index = 4,
            originClassName = "server.c",
            function = "int processCommand(client *c)",
            introduction = "执行这个函数的时候，代表已经读到了完整的命令,参数则放在client的  argv/argc 字段中"
    )
    public void processCommand(){
        // ...
        Code.SLICE.source("c->cmd = c->lastcmd = lookupCommand(c->argv[0]->ptr);")
                .interpretation("根据参数去查找命令，如果没有命令则后面抛出异常");
        //...
        Code.SLICE.source("call(c,CMD_CALL_FULL);")
                .interpretation("对于单条命令最终调用call来执行这个命令,");
        //...
    }
    @Trace(
            index = 5,
            originClassName = "server.c",
            function = "struct redisCommand *lookupCommand(sds name)"
    )
    public void lookupCommand(){
        Code.SLICE.source("return dictFetchValue(server.commands, name);")
                .interpretation("从server的command中去查找对应的字段是否存在")
                .interpretation("1:查找命令的name字段类型 sds (simple dynamic string),这个也就是命令本身存的是一个 sds")
                .interpretation("2:命令查找返回的是 redisCommand ,可见每一个redis的命令都是用结构 redisCommand统一表示")
                .interpretation("3:server是一个全局字段，表示的是 redisServer ，它会在服务启动的时候进行初始化，其中一项就包括初始化所有redis支持的命令");
    }
    @Trace(
            index = 6,
            originClassName = "server.c",
            function = "void initServerConfig(void)"
    )
    public void initServerConfig(){
        //...
        Code.SLICE.source(" server.commands = dictCreate(&commandTableDictType,NULL);\n" +
                "    server.orig_commands = dictCreate(&commandTableDictType,NULL);\n" +
                "    populateCommandTable();")
                .interpretation("在redis执行main方法时候，便会主动加载服务的配置，其中一项就是初始化命令列表");
        //...
    }
    @Trace(
            index = 7,
            originClassName = "server.c",
            function = "void populateCommandTable(void)"
    )
    public void populateCommandTable(){
        //...
        Code.SLICE.source("int numcommands = sizeof(redisCommandTable)/sizeof(struct redisCommand);")
                .interpretation("所有的命令都保存在了 redisCommandTable 中，它的格式为 {\"set\",setCommand,-3,\"wm\",0,NULL,1,1,1,0,0} ，每一项的结构都是 redisCommand，对象的第一个是命令的名字，第2项就是实现命令的方法");
        //...
        Code.SLICE.source("     retval1 = dictAdd(server.commands, sdsnew(c->name), c);")
                .interpretation("遍历所有定义好的命令，然后将每一个保存到服务的commands上，以备后续使用");
        //...
    }
    @Trace(
            index = 8,
            originClassName = "server.c",
            function = "void call(client *c, int flags)"
    )
    public void call(){
        //...
        Code.SLICE.source("c->cmd->proc(c);")
                .interpretation("执行命令，c->cmd本身就是 redisCommand ,proc 则是它的一个 redisCommandProc *proc; 的函数指针，指向函数即 typedef void redisCommandProc(client *c); ，对于set来说这个函数实现就是 setCommand");
        //...
    }
    @Trace(
            index = 9,
            originClassName = "t_string.c",
            function = "void setCommand(client *c)",
            introduction = "SET 命令的执行格式为：SET key value [NX] [XX] [EX <seconds>] [PX <milliseconds>] "
    )
    public void setCommand(){
        //...
        Code.SLICE.source("c->argv[2] = tryObjectEncoding(c->argv[2]);")
                .interpretation("在对set的格式做完语法校验，同时取得相应的命令属于 NX/XX/EX/PX/直接set之后，根据value来获取编码");
        Code.SLICE.source("setGenericCommand(c,flags,c->argv[1],c->argv[2],expire,unit,NULL,NULL);")
                .interpretation("根据实际情况存储k-v对");
    }

    @KeyPoint
    @Trace(
            index = 10,
            originClassName = "object.c",
            function = "robj *tryObjectEncoding(robj *o) ",
            introduction = "robj即结构体 redisObject ，它的结构如下：typedef struct redisObject {\n" +
                    "    unsigned type:4;\n //对象类型 比如 string,hash,list,set,zset" +
                    "    unsigned encoding:4;\n //编码类型 表明当前数据采用哪种数据结构实现" +
                    "    unsigned lru:LRU_BITS; /* LRU time (relative to global lru_clock) or\n" +
                    "                            * LFU data (least significant 8 bits frequency\n" +
                    "                            * and most significant 16 bits access time). */\n //lru计时时钟 记录对象最后一次被访问的时间" +
                    "    int refcount;\n //引用次数" +
                    "    void *ptr;\n //数据指针" +
                    "} robj;用来表示一个redis的值，也就是说暴漏出去的虽然是string,但是里面会使用 redisObject来包装一层"
    )
    public void tryObjectEncoding(){

        //...
        Code.SLICE.source("serverAssertWithInfo(NULL,o,o->type == OBJ_STRING);")
                .interpretation("确保进来的就是redisObject的类型是string");
        Code.SLICE.source("if (!sdsEncodedObject(o)) return o;")
                .interpretation("如果redisObject的编码方式不是 raw/embstr ,直接返回传入的对象");
        //...
        Code.SLICE.source("len = sdslen(s);")
                .interpretation("获取要存储的字符串值的长度，s取值即 redisObject指向的 数据字节指针");
        Code.SLICE.source("if (len <= 20 && string2l(s,len,&value))")
                .interpretation("判断字符串的长度如果小于20并且能够转成long  类型，执行转成long 的逻辑,并结果存储到value");
        //...
        Code.SLICE.source("       o->encoding = OBJ_ENCODING_INT;\n" +
                     "            o->ptr = (void*) value;")
                .interpretation("判定好是可以转成long则设定编码方式为int,同时数据指针就直接存储值");
        //...
        Code.SLICE.source("if (len <= OBJ_ENCODING_EMBSTR_SIZE_LIMIT) ")
                .interpretation("如果字符串长度满足emb的长度条件（44），使用emb编码,使得通过一次内存分配函数的调用就可以拿到连续的内存空间存储 redisObject和 数据 sdshdr");
        //...
        Code.SLICE.source("     emb = createEmbeddedStringObject(s,sdslen(s));")
                .interpretation("将值使用emb编码后再返回");
        //...
        Code.SLICE.source("if (o->encoding == OBJ_ENCODING_RAW &&\n" +
                        "        sdsavail(s) > len/10)\n" +
                        "    {\n" +
                        "        o->ptr = sdsRemoveFreeSpace(o->ptr);\n" +
                        "    }")
                .interpretation("如果超过了emb限制，则尽量的去较少浪费的空间,将原始的内容直接返回");
        //...
    }

    @Trace(
            index = 11,
            originClassName = "object.c",
            function = "robj *createEmbeddedStringObject(const char *ptr, size_t len) "
    )
    public void createEmbeddedStringObject(){
        Code.SLICE.source("robj *o = zmalloc(sizeof(robj)+sizeof(struct sdshdr8)+len+1);")
                .interpretation("指定embeddedString应该分配的内存大小为 16+3+44+1=64 其中 redisObject的标记最大为16字节，sdshdr8 len的最大长度为44字节，1字节为字符结尾标记");
        //...
    }

    @Trace(
            index = 12,
            originClassName = "t_string.c",
            function = "void setGenericCommand(client *c, int flags, robj *key, robj *val, robj *expire, int unit, robj *ok_reply, robj *abort_reply)"
    )
    public void setGenericCommand(){
        Code.SLICE.source(" if (expire) {\n" +
                "        if (getLongLongFromObjectOrReply(c, expire, &milliseconds, NULL) != C_OK)\n" +
                "            return;\n" +
                "        if (milliseconds <= 0) {\n" +
                "            addReplyErrorFormat(c,\"invalid expire time in %s\",c->cmd->name);\n" +
                "            return;\n" +
                "        }\n" +
                "        if (unit == UNIT_SECONDS) milliseconds *= 1000;\n" +
                "    }\n")
                .interpretation("如果设置了过期时间，先进行过期时间的类型转换，转换正常根据单位作出过期时间具体值的计算");
        Code.SLICE.source("  if ((flags & OBJ_SET_NX && lookupKeyWrite(c->db,key) != NULL) ||\n" +
                "        (flags & OBJ_SET_XX && lookupKeyWrite(c->db,key) == NULL))\n" +
                "    {\n" +
                "        addReply(c, abort_reply ? abort_reply : shared.nullbulk);\n" +
                "        return;\n" +
                "    }")
                .interpretation("如果是NX或者XX命令先按照条件看是否满足，满足条件才执行存储");
        //...
        Code.SLICE.source("setKey(c->db,key,val);")
                .interpretation("将用户输入的内容k-v对存下来");
        //... 后续处理过期时间相关

    }
    @Trace(
            index = 13,
            originClassName = "db.c",
            function = "void setKey(redisDb *db, robj *key, robj *val) "
    )
    public void setKey(){
        Code.SLICE.source(" if (lookupKeyWrite(db,key) == NULL) {\n" +
                "        dbAdd(db,key,val);\n" +
                "    } else {\n" +
                "        dbOverwrite(db,key,val);\n" +
                "    }")
                .interpretation("如果之前没有存过，就直接添加，否则去覆盖");
    }

    @Trace(
            index = 14,
            originClassName = "db.c",
            function = "robj *lookupKeyWrite(redisDb *db, robj *key) "
    )

    public void lookupKeyWrite(){
        Code.SLICE.source("  expireIfNeeded(db,key);\n" +
                "    return lookupKey(db,key,LOOKUP_NONE);")
                .interpretation("1:首先检查key是否已经过期了，如果是master,那么过期事件发生会传播出去，如果是slave那么只会返回过期的结果而不做具体的操作")
                .interpretation("2:从底层存储的db(实际是个dict)中查找对应的key是否存在，如果存在返回这个value,如果不存在则返回NULL")
                .interpretation("3:查找的时候会更新LRU的时间戳");
    }

    @Trace(
            index = 15,
            originClassName = "db.c",
            function = "void dbAdd(redisDb *db, robj *key, robj *val) "
    )
    public void dbAdd(){
            Code.SLICE.source(" sds copy = sdsdup(key->ptr);\n" +
                    "    int retval = dictAdd(db->dict, copy, val);")
                    .interpretation("首选拷贝出原来的值，然后再讲值加到dict中,后续增加对象的引用，处理过期等等");
    }

    @Trace(
            index = 16,
            originClassName = "sds.c",
            function = "sds sdsdup(const sds s) "
    )
    public void sdsdup(){
        Code.SLICE.source("return sdsnewlen(s, sdslen(s));")
                .interpretation("根据原有的长度新建一个sds结构");
    }

    @KeyPoint
    @Trace(
            index = 17,
            originClassName = "sds.c",
            function = "sds sdsnewlen(const void *init, size_t initlen)"
    )
    public void sdsnewlen(){
        //...
        Code.SLICE.source("char type = sdsReqType(initlen);")
                .interpretation("根据要新建的字符串获取不同的类型,类型就是宏定义的  0 1 2 3 4这5个取值的类型，代表不同的 sdshdr 结构\n");

        //...
        Code.SLICE.source("   switch(type) {\n" +
                "        case SDS_TYPE_5: {\n" +
                "            *fp = type | (initlen << SDS_TYPE_BITS);\n" +
                "            break;\n" +
                "        }\n" +
                "        case SDS_TYPE_8: {\n" +
                "            SDS_HDR_VAR(8,s);\n" +
                "            sh->len = initlen;\n" +
                "            sh->alloc = initlen;\n" +
                "            *fp = type;\n" +
                "            break;\n" +
                "        }\n" +
                "        case SDS_TYPE_16: {\n" +
                "            SDS_HDR_VAR(16,s);\n" +
                "            sh->len = initlen;\n" +
                "            sh->alloc = initlen;\n" +
                "            *fp = type;\n" +
                "            break;\n" +
                "        }\n" +
                "        case SDS_TYPE_32: {\n" +
                "            SDS_HDR_VAR(32,s);\n" +
                "            sh->len = initlen;\n" +
                "            sh->alloc = initlen;\n" +
                "            *fp = type;\n" +
                "            break;\n" +
                "        }\n" +
                "        case SDS_TYPE_64: {\n" +
                "            SDS_HDR_VAR(64,s);\n" +
                "            sh->len = initlen;\n" +
                "            sh->alloc = initlen;\n" +
                "            *fp = type;\n" +
                "            break;\n" +
                "        }\n" +
                "    }")
                .interpretation("类型不同创建不同的结构");
    }

    @Trace(
            index = 18,
            originClassName = "sds.c",
            function = "static inline char sdsReqType(size_t string_size) "
    )
    public void sdsReqType(){
        Code.SLICE.source("if (string_size < 1<<5)\n" +
                    "        return SDS_TYPE_5;\n" +
                    "    if (string_size < 1<<8)\n" +
                    "        return SDS_TYPE_8;\n" +
                    "    if (string_size < 1<<16)\n" +
                    "        return SDS_TYPE_16;\n" +
                    "#if (LONG_MAX == LLONG_MAX)\n" +
                    "    if (string_size < 1ll<<32)\n" +
                    "        return SDS_TYPE_32;\n" +
                    "    return SDS_TYPE_64;\n" +
                    "#else\n" +
                    "    return SDS_TYPE_32;\n" +
                    "#endif\n" +
                "}")
                .interpretation("根据字符串的长度，来选取不同的类型,长度小于 32用 SDS_TYPE_5 在32和256之间用SDS_TYPE_8 依此类推");
    }

    @KeyPoint
    @Trace(
            index = 19,
            originClassName = "sds.h",
            function = "struct define of sdshdr8"
    )
    public void structOfSdshdr8(){
      Code.SLICE.source("struct __attribute__ ((__packed__)) sdshdr8 {\n" +
              "    uint8_t len; /* 已经使用的长度 */\n" +
              "    uint8_t alloc; /* 分配的长度 */\n" +
              "    unsigned char flags; /* 3 lsb of type, 5 unused bits */\n" +
              "    char buf[];\n" +
              "};")
              .interpretation("len表示使用了的长度，alloc表示分配的空间长度，flags的最低三个bit用来表示header的类型,类型比如 sdshdr8")
              .interpretation("1：uint8_t指的是 unsigned char ,大小为1字节 char buf[]本身不计算大小,只是真实数据存储的时候，会在 buf最后添加 1个 \0,为了和C做兼容,方便利用C的一些函数")
              .interpretation("2:__attribute__ ((__packed__)) 是为了告诉编译器，以紧凑的方式存放，不做对齐，redis这样做方便获取数据,比如要拿到flag只需要获取 buf的前一个地址即可");
    }

}
