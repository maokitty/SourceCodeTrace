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
        target = "了解启动redis后是如何加载数据的",
        conclusion = "1:启动的时候就会执行加载AOF或者RDB；2：加载RDB的文件解析其实就是按照既定的规则执行反序列化;3:加载AOF也就是一条条的执行所有命令",
        sourceCodeProjectName = "redis",
        sourceCodeAddress = "https://github.com/antirez/redis",
        projectVersion = "5.0.0"

)
public class LoadFromDiskTrace {

    @Main
    @Trace(
            index = 0,
            originClassName = "server.c",
            function = "int main(int argc, char **argv) "
    )
    public void main(){
        //...
        Code.SLICE.source("loadDataFromDisk()")
        .interpretation("启动的时候执行从磁盘加载数据的过程");
        //...
    }

    @Trace(
            index = 1,
            originClassName = "server.c",
            function = "void loadDataFromDisk(void) "
    )
    public void loadDataFromDisk(){
        //...
        Code.SLICE.source("if (server.aof_state == AOF_ON) {" +
                "        if (loadAppendOnlyFile(server.aof_filename) == C_OK)" +
                "..." +
                "} else {" +
                "        rdbSaveInfo rsi = RDB_SAVE_INFO_INIT;" +
                "        if (rdbLoad(server.rdb_filename,&rsi) == C_OK)")
                .interpretation("如果使用的是AOF的模式，就加载AOF文件，否则加载RDB文件");


    }



    @Trace(
            index = 2,
            originClassName = "rdb.c",
            function = "int rdbLoad(char *filename, rdbSaveInfo *rsi) "
    )
    public void rdbLoad(){
        //..
        Code.SLICE.source("if ((fp = fopen(filename,\"r\")) == NULL) return C_ERR;")
                .interpretation("打开RDB的文件");
        //...
        Code.SLICE.source("retval = rdbLoadRio(&rdb,rsi,0);")
                .interpretation("读取文件中的数据");
        //...
    }

    @KeyPoint
    @Trace(
            index = 3,
            originClassName = "rdb.c",
            function = "int rdbLoadRio(rio *rdb, rdbSaveInfo *rsi, int loading_aof) "
    )
    public void rdbLoadRio(){
        //...
        Code.SLICE.source("if (rioRead(rdb,buf,9) == 0) goto eoferr;" +
                "    buf[9] = '\\0';" +
                "    if (memcmp(buf,\"REDIS\",5) != 0)")
                .interpretation("读取文件的前9个字节，前5个必定是REDIS字符，否则出错");
        //...
        Code.SLICE.source("rdbver = atoi(buf+5);" +
                "    if (rdbver < 1 || rdbver > RDB_VERSION)")
                .interpretation("紧接着读到的内容必定是 RDB的版本信息，而且不是比当前版本要大的版本");
        //...
        Code.SLICE.source("while(1) {..." +
                "if ((type = rdbLoadType(rdb)) == -1) goto eoferr;" +
                "..." +
                " else if (type == RDB_OPCODE_EOF) {" +
                "            /* EOF: End of file, exit the main loop. */" +
                "            break;" +
                "..." +
                "else if (type == RDB_OPCODE_RESIZEDB){...}" +
                "..." +
                "if ((key = rdbLoadStringObject(rdb)) == NULL) goto eoferr;" +
                "if ((val = rdbLoadObject(type,rdb)) == NULL) goto eoferr;" +
                "}")
                .interpretation("循环读取文件的内容,首先读到接下来的类型")
                .interpretation("1: 读到EOF结束")
                .interpretation("2: 读取到对应的标记，就继续读取后面的字节，直到读到key")
                .interpretation("3: 读取key,读取val");
        //...
    }

    @Trace(
            index = 4,
            originClassName = "rdb.c",
            function = "void *rdbGenericLoadStringObject(rio *rdb, int flags, size_t *lenptr)",
            introduction = "rdbLoadStringObject 实际上就是调用了rdbGenericLoadStringObject "
    )
    public void rdbGenericLoadStringObject(){
        //..
        Code.SLICE.source("len = rdbLoadLen(rdb,&isencoded);")
                .interpretation("读到接下来要读的字符串长度，并记下是否有编码");
        Code.SLICE.source("if (isencoded) {" +
                "        switch(len) {" +
                "        case RDB_ENC_INT8:" +
                "        case RDB_ENC_INT16:" +
                "        case RDB_ENC_INT32:" +
                "            return rdbLoadIntegerObject(rdb,len,flags,lenptr);" +
                "        case RDB_ENC_LZF:" +
                "            return rdbLoadLzfStringObject(rdb,flags,lenptr);" +
                "        default:" +
                "            rdbExitReportCorruptRDB(\"Unknown RDB string encoding type %d\",len);" +
                "        }" +
                "    }")
                .interpretation("有编码则看编码类型分别读取数据");
        //...
        Code.SLICE.source("robj *o = encode ? createStringObject(SDS_NOINIT,len) :" +
                "                           createRawStringObject(SDS_NOINIT,len);" +
                "        if (len && rioRead(rdb,o->ptr,len) == 0)")
                .interpretation("对于普通的key来说，如果不是数字，就直接读取对应的字符串即可");
        //..
    }

    @Trace(
            index = 5,
            originClassName = "rdb.c",
            function = "robj *rdbLoadObject(int rdbtype, rio *rdb) "
    )
    public void rdbLoadObject(){
        //...
        Code.SLICE.source("if (rdbtype == RDB_TYPE_STRING)")
                .interpretation("执行者与存储相反的过程，进行逆向解析出结果");
        //...
        Code.SLICE.source("else if (rdbtype == RDB_TYPE_HASH) {" +
                "        len = rdbLoadLen(rdb, NULL);" +
                "..." +
                "        o = createHashObject();" +
                "        /* ... */" +
                "        while (o->encoding == OBJ_ENCODING_ZIPLIST && len > 0) {" +
                "            len--;" +
                "            /* Load raw strings */" +
                "            if ((field = rdbGenericLoadStringObject(rdb,RDB_LOAD_SDS,NULL))" +
                "                == NULL) return NULL;" +
                "            if ((value = rdbGenericLoadStringObject(rdb,RDB_LOAD_SDS,NULL))" +
                "                == NULL) return NULL;" +
                "" +
                "            /* Add pair to ziplist */" +
                "            o->ptr = ziplistPush(o->ptr, (unsigned char*)field," +
                "                    sdslen(field), ZIPLIST_TAIL);" +
                "            o->ptr = ziplistPush(o->ptr, (unsigned char*)value," +
                "                    sdslen(value), ZIPLIST_TAIL);" +
                "" +
                "            /* Convert to hash table if size threshold is exceeded */" +
                "            if (sdslen(field) > server.hash_max_ziplist_value ||" +
                "                sdslen(value) > server.hash_max_ziplist_value)" +
                "            {" +
                "                sdsfree(field);" +
                "                sdsfree(value);" +
                "                hashTypeConvert(o, OBJ_ENCODING_HT);" +
                "                break;" +
                "            }" +
                "            sdsfree(field);" +
                "            sdsfree(value);" +
                "        }" +
                " ........"+
                "        /* Load remaining fields and values into the hash table */" +
                "        while (o->encoding == OBJ_ENCODING_HT && len > 0) {" +
                "            len--;" +
                "            /* Load encoded strings */" +
                "            if ((field = rdbGenericLoadStringObject(rdb,RDB_LOAD_SDS,NULL))" +
                "                == NULL) return NULL;" +
                "            if ((value = rdbGenericLoadStringObject(rdb,RDB_LOAD_SDS,NULL))" +
                "                == NULL) return NULL;" +
                "" +
                "            /* Add pair to hash table */" +
                "            ret = dictAdd((dict*)o->ptr, field, value);" +
                "            if (ret == DICT_ERR) {" +
                "                rdbExitReportCorruptRDB(\"Duplicate keys detected\");" +
                "            }" +
                "        }" +
                "    }")
                .interpretation("以hashtable为例，读取到对应的数据长度，创建对象，根据对象的编码方式，分别解析成ziplist或者是hashtable来存储");
        //...
    }

    @Trace(
            index = 6,
            originClassName = "aof.c",
            function = "int loadAppendOnlyFile(char *filename) "
    )
    public void  loadAppendOnlyFile(){
        //..
        Code.SLICE.source("FILE *fp = fopen(filename,\"r\");")
                .interpretation("打开AOF文件");
        //..
        Code.SLICE.source("while(1) ")
                .interpretation("读取AOF文件的内容，按照 REPL 的格式 ，1条条命令处理");
        //..
        Code.SLICE.source("if (fgets(buf,sizeof(buf),fp) == NULL)")
                .interpretation("从文件中读取一定字节到buf中");
        //..
        Code.SLICE.source("argc = atoi(buf+1);")
                .interpretation("拿到命令的长度");
        //..
        Code.SLICE.source("for (j = 0; j < argc; j++) ")
                .interpretation("一个个的解析这条命令的所有数据,中间会对写入的数据做校验");
        //..
        Code.SLICE.source("argv[j] = createObject(OBJ_STRING,argsds);")
                .interpretation("将数据存储到argv数组");
        //..
        Code.SLICE.source("cmd = lookupCommand(argv[0]->ptr);")
                .interpretation("确保要执行的命令是合法的redis命令");
        //..
        Code.SLICE.source("cmd->proc(fakeClient);")
                .interpretation("模拟执行");
        //..
        Code.SLICE.source("fclose(fp);" +
                "    freeFakeClient(fakeClient);" +
                "    server.aof_state = old_aof_state;" +
                "    stopLoading(1);" +
                "    aofUpdateCurrentSize();" +
                "    server.aof_rewrite_base_size = server.aof_current_size;" +
                "    server.aof_fsync_offset = server.aof_current_size;" +
                "    return C_OK;")
                .interpretation("加载完成");
    }



}
