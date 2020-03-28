package paxi.maokitty.source.redis;

import paxi.maokitty.source.annotation.Background;
import paxi.maokitty.source.annotation.KeyPoint;
import paxi.maokitty.source.annotation.Main;
import paxi.maokitty.source.annotation.Trace;
import paxi.maokitty.source.util.Code;

/**
 * Created by maokitty on 19/7/7.
 */
@Background(
        target = "了解用户执行rpush之后，redis底层的支持list方式",
        conclusion = "list在底层会使用quicklist的结构来存储，每一个quicklistNode的节点都会存储一个可配置的ziplist大小量，如果有多个quicklistNode，它会根据配置的压缩深度，来使用lzf算法进行压缩",
        sourceCodeProjectName = "redis",
        sourceCodeAddress = "https://github.com/antirez/redis",
        projectVersion = "5.0.0"

)
public class RpushCommandExecuteTrace {
    @Main
    @Trace(
            index = 0,
            originClassName = "t_list.c",
            function = "void pushGenericCommand(client *c, int where)",
            introduction = "redis执行具体的rpush函数的方法为 rpushCommand,where则指代的是 队尾,表示要从队尾插入"
    )

    public void pushGenericCommand(){
        //...
        Code.SLICE.source("robj *lobj = lookupKeyWrite(c->db,c->argv[1]);" +
                "" +
                "    if (lobj && lobj->type != OBJ_LIST) {" +
                "        addReply(c,shared.wrongtypeerr);" +
                "        return;" +
                "    }")
                .interpretation("查找之前是不是有过同名的key,如果有，但是key的编码方式不是 OBJ_LIST直接报错返回");
        Code.SLICE.source("for (j = 2; j < c->argc; j++) ")
                .interpretation("遍历所有的value,一个个的插入");
        Code.SLICE.source("if (!lobj) {" +
                "            lobj = createQuicklistObject();" +
                "            quicklistSetOptions(lobj->ptr, server.list_max_ziplist_size," +
                "                                server.list_compress_depth);" +
                "            dbAdd(c->db,c->argv[1],lobj);" +
                "        }" +
                "        listTypePush(lobj,c->argv[j],where);" +
                "        pushed++;")
                .interpretation("如果之前没有存在一模一样的key,重新创建一个，它的类型是 quicklist,然后存起来，再执行插入");
        //...
    }
    @Trace(
            index = 1,
            originClassName = "object.c",
            function = "robj *createQuicklistObject(void) "
    )
    public void createQuicklistObject(){
        Code.SLICE.source("quicklist *l = quicklistCreate();" +
                "    robj *o = createObject(OBJ_LIST,l);" +
                "    o->encoding = OBJ_ENCODING_QUICKLIST;" +
                "    return o;")
                .interpretation("redisobject的数据类型是Obj_list,编码方式使用的是 quicklist,数据指向的也是 quicklist结构");
    }

    @KeyPoint
    @Trace(
            index = 2,
            originClassName = "quicklist.h",
            function = "typedef structure",
            introduction = "创建的quicklist的结构"
    )
    public void typeDefineOfQuickList(){
        Code.SLICE.source("typedef struct quicklist {" +
                "    quicklistNode *head;        /*头结点*/" +
                "    quicklistNode *tail;        /*尾结点*/" +
                "    unsigned long count;        /* 所有ziplists中的所有entry的个数 */" +
                "    unsigned long len;          /* quicklistNodes节点的个数 */" +
                "    int fill : 16;              /* ziplist大小设置，存放配置 list-max-ziplist-size */" +
                "    unsigned int compress : 16; /* 节点压缩深度设置，存放配置 list_compress_depth */" +
                "} quicklist;")
                .interpretation("head和tail两个函数指针最多8字节,count和len属于无符号long最多8字节，最后两字段共32bits，总共40字节")
                .interpretation("list-max-ziplist-size 取正数按照个数来限制ziplist的大小，比如5表示每个quicklist节点ziplist最多包含5个数据项，最大为 1 << 15" +
                        "-1表示每个quicklist节点上的ziplist大小不能超过 4kb,-2(默认值)表示不能超过 8kb依次类推，最大为 -5，不能超过 64kb")
                .interpretation("list_compress_depth 0表示不压缩,1表示quicklist两端各有1个节点不压缩，其余压缩，2表示quicklist两端各有2个节点不压缩，其余压缩，依次类推，最大为 1 << 16");
        //...
        Code.SLICE.source("typedef struct quicklistNode {" +
                "    struct quicklistNode *prev;  /*当前节点的前一个结点*/" +
                "    struct quicklistNode *next;  /*当前节点的下一个结点*/" +
                "    unsigned char *zl;           /*数据指针。如果当前节点没有被压缩，它指向的是一个ziplist,否则是 quicklistLZF*/" +
                "    unsigned int sz;             /* zl所指向的 ziplist 的总大小，计算被压缩了，指向的也是压缩前的大小*/" +
                "    unsigned int count : 16;     /* ziplist中数据项的个数 */" +
                "    unsigned int encoding : 2;   /* RAW==1（没有压缩） or LZF==2（压缩了） */" +
                "    unsigned int container : 2;  /* NONE==1 or ZIPLIST==2 */" +
                "    unsigned int recompress : 1; /* 识别这个数据之前是不是压缩过，比如再检查数据的过程中是要解压缩的过后需要还原*/" +
                "    unsigned int attempted_compress : 1; /* node can't compress; too small */" +
                "    unsigned int extra : 10; /* 扩展字段，目前没有用*/" +
                "} quicklistNode;")
                .interpretation("从前向和后项来看,quickList 本身就是一个 双向链表")
                .interpretation("1:结构自身的大小 prev、next、zl 各8字节,sz无符号 int 为4字节，其余按照后面的bit算一共32bits共4字节，总共32字节");

    }

    @Trace(
            index = 3,
            originClassName = "t_list.h",
            function = "void listTypePush(robj *subject, robj *value, int where) "
    )
    public void listTypePush(){
        //...
        Code.SLICE.source("quicklistPush(subject->ptr, value->ptr, len, pos);")
                .interpretation("subject即key,value即要存储的值，len是要存储值的长度，pos代表是在队头还是队尾插入,根据插入位置不同，执行不同的方法，以quicklistPushTail为例");
        //...
    }

    @Trace(
            index = 4,
            originClassName = "quicklist.c",
            function = "int quicklistPushTail(quicklist *quicklist, void *value, size_t sz)"
    )
    public void quicklistPushTail(){
        Code.SLICE.source("quicklistNode *orig_tail = quicklist->tail;")
                .interpretation("获取当前key的队尾");
        Code.SLICE.source("if (likely(" +
                "            _quicklistNodeAllowInsert(quicklist->tail, quicklist->fill, sz))) {" +
                "        quicklist->tail->zl =" +
                "            ziplistPush(quicklist->tail->zl, value, sz, ZIPLIST_TAIL);" +
                "        quicklistNodeUpdateSz(quicklist->tail);" +
                "    } ")
                .interpretation("likely的含义是很有可能条件表达式为真，即要紧跟着执行if里面的内容，也就是直接插入当前节点的ziplist即可,判断是否能插入当前节点，则会根据ziplist的大小设置来看");
        Code.SLICE.source("quicklistNode *node = quicklistCreateNode();" +
                "        node->zl = ziplistPush(ziplistNew(), value, sz, ZIPLIST_TAIL);" +
                "        quicklistNodeUpdateSz(node);" +
                "        _quicklistInsertNodeAfter(quicklist, quicklist->tail, node);")
                .interpretation("如果当前节点插不进去，需要新建一个quicklistNode,在新的节点里面存放内容");
        //...
    }

    @Trace(
            index = 5,
            originClassName = "ziplist.c",
            function = "unsigned char *ziplistNew(void) "
    )
    public void ziplistNew(){
        Code.SLICE.source("  unsigned int bytes = ZIPLIST_HEADER_SIZE+1;" +
                "    unsigned char *zl = zmalloc(bytes);" +
                "    ZIPLIST_BYTES(zl) = intrev32ifbe(bytes);" +
                "    ZIPLIST_TAIL_OFFSET(zl) = intrev32ifbe(ZIPLIST_HEADER_SIZE);" +
                "    ZIPLIST_LENGTH(zl) = 0;" +
                "    zl[bytes-1] = ZIP_END;" +
                "    return zl;")
                .interpretation("ziplist的结构为 zlbytes|zltail|zllen|engtry1|..|entryN|zlend")
                .interpretation("1:zlbytes为4字节，记录整个ziplist占用的大小")
                .interpretation("2:zltail为4字节，记录ziplist的尾部距离ziplist起始地址的偏移量")
                .interpretation("3:zllen为2字节，当值小于 65535 时，表示ziplist包含节点数，等于 65535则表示需要遍历整个压缩列表才能计算出来")
                .interpretation("4:entry表示压缩列表的各个节点，长度由节点保存的内容决定")
                .interpretation("5:zlend固定为255，表示ziplist的结尾");
    }

    @Trace(
            index = 6,
            originClassName = "ziplist.c",
            function = "typedef of zlentry ",
            introduction = "没有代码说明，注释"

    )
    public void typedefineOfZlentry(){
        Code.SLICE.source("")
                .interpretation("entry的结构是<prevlen> <encoding> <entry-data>")
                .interpretation("1:prevlen代表前一个entry的长度，如果它小于254字节，整个结构类似 <prevlen from 0 to 253> <encoding> <entry> ，如果大于等于254 entry结构类似 0xFE <4 bytes unsigned little endian prevlen> <encoding> <entry>")
                .interpretation("2:encoding取决于存储的内容，如果最高位是11，那么代表存的是数字，最高两位的其它01组合则表示存的是字符,根据组合的不同后面会用不同长度的字节表示对应存储的长度")
                .interpretation("3:entry-data用来保存存储节点的值");
    }
    @Trace(
            index = 7,
            originClassName = "ziplist.c",
            function = "unsigned char *__ziplistInsert(unsigned char *zl, unsigned char *p, unsigned char *s, unsigned int slen)",
            introduction = "ziplistPush的实现核心  以从队尾插入为例，" +
                    "zl表示表示listnode队尾的ziplist" +
                    "p根据是队尾插入还是队首插入，分别表示是当前ziplist的entry的首部和尾部(跳过头部指向第一个entry,尾部则指向最后FF标识) " +
                    "s 表示要存入的值" +
                    "slen表示要存入值的字节数"
    )
    public void __ziplistInsert(){
        //...
        Code.SLICE.source("if (p[0] != ZIP_END) {" +
                "        ZIP_DECODE_PREVLEN(p, prevlensize, prevlen);" +
                "    } else {" +
                "        unsigned char *ptail = ZIPLIST_ENTRY_TAIL(zl);" +
                "        if (ptail[0] != ZIP_END) {" +
                "            prevlen = zipRawEntryLength(ptail);" +
                "        }" +
                "    }")
                .interpretation("获取prevlen的值，它将被存在新节点的 prevrawlen 中，如果是从队尾插入，此时p的指向的 结尾表示，此时要获取真正的队尾entry,如果之前没有entry,说明这是第一个被插入的");
        Code.SLICE.source(" /* See if the entry can be encoded */" +
                "    if (zipTryEncoding(s,slen,&value,&encoding)) {" +
                "        /* 'encoding' is set to the appropriate integer encoding */" +
                "        reqlen = zipIntSize(encoding);" +
                "    } else {" +
                "        /* 'encoding' is untouched, however zipStoreEntryEncoding will use the" +
                "         * string length to figure out how to encode it. */" +
                "        reqlen = slen;" +
                "    }")
                .interpretation("查看传入的值能否被转成int类型，如果可以则存下int的字节长度，否则存下原有的字节数");
        Code.SLICE.source("reqlen += zipStorePrevEntryLength(NULL,prevlen);")
                .interpretation("根据prevlen的大小，计算 prevrawlenSize 应该占据的大小，如果 prevlen小于 254,只用1字节就可以，否则用5字节");
        Code.SLICE.source("reqlen += zipStoreEntryEncoding(NULL,encoding,slen);")
                .interpretation("计算出encoding所需要占据的长度以及要存入的字符的长度，作为需要新增的空间的一部分");
        //..
        Code.SLICE.source("nextdiff = (p[0] != ZIP_END) ? zipPrevLenByteDiff(p,reqlen) : 0;")
                .interpretation("如果不是在队尾插入，需要计算当前节点和新插入节点字节变化的差值，zipPrevLenByteDiff如果返回证正数说明需要更多的空间，如果是负数空间缩减了，如果是0说明没有变化");
        //..
        Code.SLICE.source("offset = p-zl;")
                .interpretation("记下调整前队尾/首的偏移量");
        Code.SLICE.source("zl = ziplistResize(zl,curlen+reqlen+nextdiff);")
                .interpretation("根据算出来的新元素的大小，以及是否有重新调整原prevlen，来调整ziplist的大小");
        Code.SLICE.source("p = zl+offset;")
                .interpretation("记下新的p的位置,(根据条件不同，p的含义不一样，要么ziplist的队首，要么是指向原来的队尾，此时的位置刚好可以插入新的元素)");
        Code.SLICE.source("if (p[0] != ZIP_END){" +
                     "...." +
                "}else{" +
                    "ZIPLIST_TAIL_OFFSET(zl) = intrev32ifbe(p-zl);" +
                "} ")
                .interpretation("如果是队首，重新计算队尾的偏移量,否则当前的偏移量就是队尾");
        Code.SLICE.source(" if (nextdiff != 0) {" +
                "        offset = p-zl;" +
                "        zl = __ziplistCascadeUpdate(zl,p+reqlen);" +
                "        p = zl+offset;" +
                "    }")
                .interpretation("如果从队首插入，新插入的节点所需空间有变化，则级联的往后移动节点，更新后续所有节点,并记下最新的p的位置")
        .interpretation("连锁更新的发生情况为：假如原来的节点存储的prevlen是1但是新插入的节点是5字节，假设之前存储的所有的 entry都是小于254字节，那么由于新的5字节长度的插入，后续所有的节点都得重新分配,而每一次的分配" +
                "都意味着元素的后移，所以最坏的情况需要O(N^2)");

        Code.SLICE.source("p += zipStorePrevEntryLength(p,prevlen);")
                .interpretation("记下新的节点的前一个元素的长度");
        Code.SLICE.source("p += zipStoreEntryEncoding(p,encoding,slen);")
                .interpretation("记下编码方式以及存储的数据的长度");
        Code.SLICE.source("if (ZIP_IS_STR(encoding)) {" +
                "        memcpy(p,s,slen);" +
                "    } else {" +
                "        zipSaveInteger(p,value,encoding);" +
                "    }")
                .interpretation("存储新插入的值");
        Code.SLICE.source("ZIPLIST_INCR_LENGTH(zl,1);")
                .interpretation("更新ziplist的长度标识");
        //...
    }

    @Trace(
            index = 8,
            originClassName = "quicklist.c",
            function = "REDIS_STATIC void __quicklistInsertNode(quicklist *quicklist, quicklistNode *old_node,quicklistNode *new_node, int after)",
            introduction = "如果原有的quicklistnode存储不下，则会创建一个新的节点，然后将新节点链入 quicklist"
    )
    public void  __quicklistInsertNode(){
        //...
        Code.SLICE.source("if (old_node)" +
                "        quicklistCompress(quicklist, old_node);")
                .interpretation("作为指针的交换之后，如果原先是存在quicklistNode，则考虑是否需要对它进行压缩,压缩分成两种情况")
                .interpretation("1:这个节点原先是压缩过的，即recompress为1，立马压缩")
                .interpretation("2:深度超过设定的list_compress_depth");
        //...
    }
    @Trace(
            index = 9,
            originClassName = "quicklist.c",
            function = "REDIS_STATIC void __quicklistCompress(const quicklist *quicklist,quicklistNode *node) "
    )
    public void __quicklistCompress(){
        Code.SLICE.source("if (!quicklistAllowsCompression(quicklist) ||" +
                "        quicklist->len < (unsigned int)(quicklist->compress * 2))" +
                "        return;")
                .interpretation("没有设置压缩标识或者没有达到标识的深度，不压缩");
        //...
        Code.SLICE.source("while (depth++ < quicklist->compress){" +
                "quicklistDecompressNode(forward);" +
                "quicklistDecompressNode(reverse);" +
                "..." +
                "}")
                .interpretation("对于深度要求之外的进行解压缩");
        //..
        Code.SLICE.source("if (!in_depth)" +
                "        quicklistCompressNode(node);")
                .interpretation("如果在上述解压缩的过程中没有碰到新的node，那么这个节点必须进行压缩,具体压缩的方法为使用 lzf 压缩算法");
        //...
    }







}
