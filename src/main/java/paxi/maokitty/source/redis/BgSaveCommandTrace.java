package paxi.maokitty.source.redis;

import paxi.maokitty.source.annotation.Background;
import paxi.maokitty.source.annotation.KeyPoint;
import paxi.maokitty.source.annotation.Main;
import paxi.maokitty.source.annotation.Trace;
import paxi.maokitty.source.util.Code;

/**
 * Created by maokitty on 20/3/28.
 */
@Background(
        target = "了解用户执行bgSave之后，系统的运行过程，数据是如何存储的",
        conclusion = "1:bgsave不会阻塞redis其它命令的运行，通过fork子进程实现；2:RDB序列化内存对象的机制是先设定数据的类型表示，然后记下数据量，再记下数据值的长度，再记下数据本身",
        sourceCodeProjectName = "redis",
        sourceCodeAddress = "https://github.com/antirez/redis",
        projectVersion = "5.0.0"

)
public class BgSaveCommandTrace {

    @Main
    @Trace(
            index = 0,
            originClassName = "rdb.c",
            function = "void bgsaveCommand(client *c) "
    )
    public void bgsaveCommand(){
        //...
        Code.SLICE.source("if (server.rdb_child_pid != -1) {" +
                "        addReplyError(c,\"Background save already in progress\");" +
                "    } else if (server.aof_child_pid != -1) {" +
                "        if (schedule) {" +
                "            server.rdb_bgsave_scheduled = 1;" +
                "            addReplyStatus(c,\"Background saving scheduled\");" +
                "        } else {" +
                "            addReplyError(c," +
                "                \"An AOF log rewriting in progress: can't BGSAVE right now. \"" +
                "                \"Use BGSAVE SCHEDULE in order to schedule a BGSAVE whenever \"" +
                "                \"possible.\");" +
                "        }" +
                "    } else if (rdbSaveBackground(server.rdb_filename,rsiptr) == C_OK) {" +
                "        addReplyStatus(c,\"Background saving started\");" +
                "    }")
                .interpretation("在执行bgsave之前，先检查之前是否有 bgsave或者aof执行")
                .interpretation("1: 如果已经有bgsave在执行，那么放弃本次执行")
                .interpretation("2: 如果有aof执行，并且 bgsave携带了参数 schedule ,redis会在后台有个定时器轮询，是否要执行 bgsave;否则就报错")
                .interpretation("3: 当前即没有 bgsave也没有aof那么就开始执行");
        //..
    }

    @KeyPoint
    @Trace(
            index = 1,
            originClassName = "rdb.c",
            function = "int rdbSaveBackground(char *filename, rdbSaveInfo *rsi)"
    )
    public void rdbSaveBackground(){
        //..
        Code.SLICE.source("server.dirty_before_bgsave = server.dirty;" +
                "    server.lastbgsave_try = time(NULL);")
                .interpretation("1：dirty 记下上一次执行命令后，到本次执行总共有多少次的更改")
                .interpretation("2: lastbgsave_try 记录本次执行的时间");
        Code.SLICE.source("openChildInfoPipe();")
                .interpretation("开启一个父子进程之间的管道");
        Code.SLICE.source("if ((childpid = fork()) == 0) {" +
                "        //..." +
                "        retval = rdbSave(filename,rsi);" +
                "        if (retval == C_OK) {" +
                "        //..." +
                "            server.child_info_data.cow_size = private_dirty;" +
                "            sendChildInfo(CHILD_INFO_TYPE_RDB);" +
                "        }" +
                "        exitFromChild((retval == C_OK) ? 0 : 1);" +
                "    } else {" +
                "        /* Parent */" +
                "        //..." +
                "        server.rdb_save_time_start = time(NULL);" +
                "        server.rdb_child_pid = childpid;" +
                "        server.rdb_child_type = RDB_CHILD_TYPE_DISK;" +
                "        updateDictResizePolicy();" +
                "        return C_OK;" +
                "    }")
                .interpretation("创建子进程，子进程负责做rdb相关的处理，父进程记下处理中的子进程ID，返回当前bgsave的执行,也就是说bgsave不会阻塞其它命令的执行");
        //...
    }

    @Trace(
            index = 2,
            originClassName = "rdb.c",
            function = "int rdbSave(char *filename, rdbSaveInfo *rsi)"
    )
    public void rdbSave(){
        //...
        Code.SLICE.source("snprintf(tmpfile,256,\"temp-%d.rdb\", (int) getpid());" +
                "    fp = fopen(tmpfile,\"w\");")
                .interpretation("拿到rdb文件的名字，并打开这个文件");
        //...
        Code.SLICE.source("if (server.rdb_save_incremental_fsync)" +
                "        rioSetAutoSync(&rdb,REDIS_AUTOSYNC_BYTES);")
                .interpretation("根据设置，进行一部分一部分字节的自动写入到磁盘，使得磁盘的写入相对均匀");
        //...
        Code.SLICE.source("if (rdbSaveRio(&rdb,&error,RDB_SAVE_NONE,rsi) == C_ERR)")
                .interpretation("执行写的具体操作");
        //...
        Code.SLICE.source("server.dirty = 0;" +
                "    server.lastsave = time(NULL);" +
                "    server.lastbgsave_status = C_OK;")
                .interpretation("save完成，更新修改标记和修改时间");
        //...
    }

    @Trace(
            index = 3,
            originClassName = "rdb.c",
            function = "int rdbSaveRio(rio *rdb, int *error, int flags, rdbSaveInfo *rsi) "
    )
    public void rdbSaveRio(){
        //...
        Code.SLICE.source("snprintf(magic,sizeof(magic),\"REDIS%04d\",RDB_VERSION);" +
                "    if (rdbWriteRaw(rdb,magic,9) == -1) goto werr;")
                .interpretation("首先在文件中写下 REDIS字符串和RDB的版本");
        //...
        Code.SLICE.source("for (j = 0; j < server.dbnum; j++) ")
                .interpretation("遍历server内存中所有的数据库")
                .interpretation("1：默认有16个，一般用的是第0个 ,通过select 命令可以切换数据库");

        Code.SLICE.source("      redisDb *db = server.db+j;" +
                "        dict *d = db->dict;" +
                "        if (dictSize(d) == 0) continue;" +
                "        di = dictGetSafeIterator(d);")
                .interpretation("获取数据库中的db的地址，如果存在元素，创建一个迭代器进行访问");

        Code.SLICE.source("if (rdbSaveType(rdb,RDB_OPCODE_SELECTDB) == -1) goto werr;" +
                "        if (rdbSaveLen(rdb,j) == -1) goto werr;")
                .interpretation("写入一个selectdb的标识，表明接下来的存入数据库中的是第几号数据库");
        //...
        Code.SLICE.source("if (rdbSaveType(rdb,RDB_OPCODE_RESIZEDB) == -1) goto werr;" +
                "        if (rdbSaveLen(rdb,db_size) == -1) goto werr;" +
                "        if (rdbSaveLen(rdb,expires_size) == -1) goto werr;")
                .interpretation("写入 resizeDb 的标识，表明接下来的数据是resize后的db的大小，然后是过期的数量");

        Code.SLICE.source("while((de = dictNext(di)) != NULL) ")
                .interpretation("只要还有只，就拿到当前存储的key - value 做存储的处理");

        Code.SLICE.source("sds keystr = dictGetKey(de);" +
                "            robj key, *o = dictGetVal(de);")
                .interpretation("获取当前要写入的key和它的value");
        //...
        Code.SLICE.source("expire = getExpire(db,&key);")
                .interpretation("拿到当前key的过期时间");
        Code.SLICE.source("if (rdbSaveKeyValuePair(rdb,&key,o,expire) == -1) goto werr;")
                .interpretation("存储这个键值对");

        //...
        Code.SLICE.source("if (rdbSaveType(rdb,RDB_OPCODE_EOF) == -1) goto werr;")
                .interpretation("写入EOF标记，代表所有db的数据都已经写入了");
        Code.SLICE.source("cksum = rdb->cksum;" +
                "    memrev64ifbe(&cksum);" +
                "    if (rioWrite(rdb,&cksum,8) == 0) goto werr;")
                .interpretation("写入校验和，完整的内存数据写入完毕");
    }

    @Trace(
            index = 4,
            originClassName = "rdb.c",
            function = "int rdbSaveKeyValuePair(rio *rdb, robj *key, robj *val, long long expiretime)"
    )
    public void rdbSaveKeyValuePair(){
        //...
        Code.SLICE.source("if (expiretime != -1) {" +
                "        if (rdbSaveType(rdb,RDB_OPCODE_EXPIRETIME_MS) == -1) return -1;" +
                "        if (rdbSaveMillisecondTime(rdb,expiretime) == -1) return -1;" +
                "    }")
                .interpretation("如果存在过期时间，那么写入标记 EXPIRETIME_MS ，表示这个节点将在以毫秒位单位的时间过期");
        //...
        Code.SLICE.source("if (rdbSaveType(rdb,RDB_OPCODE_IDLE) == -1) return -1;" +
                "        if (rdbSaveLen(rdb,idletime) == -1) return -1;")
                .interpretation("如果有lre策略，那么记下这个key有多久没有被访问了");
        //...
        Code.SLICE.source("if (rdbSaveType(rdb,RDB_OPCODE_FREQ) == -1) return -1;" +
                "        if (rdbWriteRaw(rdb,buf,1) == -1) return -1;")
                .interpretation("如果有LFU策略，记下这个key的访问频率");

        Code.SLICE.source("if (rdbSaveObjectType(rdb,val) == -1) return -1;" +
                "    if (rdbSaveStringObject(rdb,key) == -1) return -1;" +
                "    if (rdbSaveObject(rdb,val) == -1) return -1;")
                .interpretation("依次记下这个key的数据类型，key本身和value本身");
    }

    @Trace(
            index = 5,
            originClassName = "rdb.c",
            function = "int rdbSaveObjectType(rio *rdb, robj *o) "
    )
    public void rdbSaveObjectType(){
        Code.SLICE.source(" switch (o->type) {" +
                "    case OBJ_STRING:" +
                "        return rdbSaveType(rdb,RDB_TYPE_STRING);" +
                "    case OBJ_LIST:" +
                "        if (o->encoding == OBJ_ENCODING_QUICKLIST)" +
                "            return rdbSaveType(rdb,RDB_TYPE_LIST_QUICKLIST);" +
                "        else" +
                "            serverPanic(\"Unknown list encoding\");")
                .interpretation("根据对象额类型，分别记下不同的标识");
        //..
    }

    @Trace(
            index = 6,
            originClassName = "rdb.c",
            function = "ssize_t rdbSaveStringObject(rio *rdb, robj *obj)"
    )
    public void rdbSaveStringObject(){
        Code.SLICE.source("if (obj->encoding == OBJ_ENCODING_INT) {" +
                "        return rdbSaveLongLongAsStringObject(rdb,(long)obj->ptr);" +
                "    } else {" +
                "        serverAssertWithInfo(NULL,obj,sdsEncodedObject(obj));" +
                "        return rdbSaveRawString(rdb,obj->ptr,sdslen(obj->ptr));" +
                "    }")
                .interpretation("key的存储格式最终还是按照 [len][data]的格式存储在磁盘上,如果是能转成数字数字，则按照数字的格式存储，缩减空间 ");
    }
    @Trace(
            index = 7,
            originClassName = "rdb.c",
            function = "ssize_t rdbSaveObject(rio *rdb, robj *o) "
    )
    public void rdbSaveObject(){
        //...
        Code.SLICE.source("if (o->type == OBJ_STRING) ")
                .interpretation("根据value的不同存储结构，以不同的形式存储");
        //...
        Code.SLICE.source("else if (o->type == OBJ_HASH) {" +
                "        /* Save a hash value */" +
                "        if (o->encoding == OBJ_ENCODING_ZIPLIST) {" +
                "            size_t l = ziplistBlobLen((unsigned char*)o->ptr);" +
                "" +
                "            if ((n = rdbSaveRawString(rdb,o->ptr,l)) == -1) return -1;" +
                "            nwritten += n;" +
                "" +
                "        } else if (o->encoding == OBJ_ENCODING_HT) {" +
                "            dictIterator *di = dictGetIterator(o->ptr);" +
                "            dictEntry *de;" +
                "" +
                "            if ((n = rdbSaveLen(rdb,dictSize((dict*)o->ptr))) == -1) {" +
                "                dictReleaseIterator(di);" +
                "                return -1;" +
                "            }" +
                "            nwritten += n;" +
                "" +
                "            while((de = dictNext(di)) != NULL) {" +
                "                sds field = dictGetKey(de);" +
                "                sds value = dictGetVal(de);" +
                "" +
                "                if ((n = rdbSaveRawString(rdb,(unsigned char*)field," +
                "                        sdslen(field))) == -1)" +
                "                {" +
                "                    dictReleaseIterator(di);" +
                "                    return -1;" +
                "                }" +
                "                nwritten += n;" +
                "                if ((n = rdbSaveRawString(rdb,(unsigned char*)value," +
                "                        sdslen(value))) == -1)" +
                "                {" +
                "                    dictReleaseIterator(di);" +
                "                    return -1;" +
                "                }" +
                "                nwritten += n;" +
                "            }" +
                "            dictReleaseIterator(di);" +
                "        } else {" +
                "            serverPanic(\"Unknown hash encoding\");" +
                "        }" +
                "    } ")
                .interpretation("以hash的编码方式为例，看底层的实现")
                .interpretation("1: hash的底层实现如果是ziplist，那么拿到ziplist的长度，将ziplist转为字符串存储")
                .interpretation("2: hash的底层实现方式为 hasttable,那么一个个的遍历key,value,将它们分别转成String的形式再存储");
    }












}
