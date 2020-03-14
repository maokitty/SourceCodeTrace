package paxi.maokitty.source.redis;

import paxi.maokitty.source.annotation.Background;
import paxi.maokitty.source.annotation.KeyPoint;
import paxi.maokitty.source.annotation.Main;
import paxi.maokitty.source.annotation.Trace;
import paxi.maokitty.source.util.Code;

/**
 * Created by maokitty on 20/2/25.
 */
@Background(
        target = "了解用户调用了zadd命令之后，redis执行的过程，是如何插入新元素的",
        conclusion = "1:如果节点不多，允许使用ziplist会直接使用ziplist,超过阈值使用跳表;2:每个跳表节点维护了层级结构，没有冗余的存储多余的key和score;只有最底下一层是双向链表;3:随机增长层级是通过random实现",
        sourceCodeProjectName = "redis",
        sourceCodeAddress = "https://github.com/antirez/redis",
        projectVersion = "5.0.0"
)
public class ZaddCommandExecuteTrace {

     @Main
     @Trace(
             index= 0,
             originClassName = "t_zset.c",
             function = "void zaddGenericCommand(client *c, int flags) ",
             introduction = "ZADD 和 ZINCRBY 公用的同一个方法"
     )
     public void zaddGenericCommand(){
             //...
             Code.SLICE.source("zobj = lookupKeyWrite(c->db,key);")
                     .interpretation("查看现在redis中有没有存储过这个key");
             //...
             Code.SLICE.source("if (server.zset_max_ziplist_entries == 0 ||" +
                     "            server.zset_max_ziplist_value < sdslen(c->argv[scoreidx+1]->ptr))" +
                     "        {" +
                     "            zobj = createZsetObject();" +
                     "        } else {" +
                     "            zobj = createZsetZiplistObject();" +
                     "        }" +
                     "        dbAdd(c->db,key,zobj);")
                     .interpretation("如果key不存在，创建一个key对象，并把这个key对象存储起来")
                     .interpretation("1: zset_max_ziplist_entries 和 zset_max_ziplist_value 是用来配置服务端是否只用ziplist,zset_max_ziplist_entries 默认值为128 ；zset_max_ziplist_value 默认值是 64")
                     .interpretation("2: 如果不允许使用ziplist或者 或者 存储的元素长度超过 64，则使用 Zset 结构，否则使用 ziplist");
             //...
             Code.SLICE.source("for (j = 0; j < elements; j++) {" +
                     "//..." +
                     "score = scores[j];" +
                     "//..." +
                     "ele = c->argv[scoreidx+1+j*2]->ptr;" +
                     "//.." +
                     "int retval = zsetAdd(zobj, score, ele, &retflags, &newscore);" +
                     "//..." +
                     "}")
                     .interpretation("遍历所有输入的要放入排序集合的元素")
                     .interpretation("1: zadd命令肯定输入的参数肯定包括一个score和一个key,那么只需要一半的所有参数长度边可以遍历所有的score和score对应的key")
                     .interpretation("2: ele就是要放入Zset的key,score就是这个key对应的分数.zobj则是这个排序列表的key,")   ;
             //...
     }

        @Trace(
                index= 1,
                originClassName = "server.h",
                function = "typeDefineOfZset",
                introduction = "构建Zset结构"
        )
        public void typeDefineOfZset(){
                Code.SLICE.source("typedef struct zset {" +
                        "    dict *dict;" +
                        "    zskiplist *zsl;" +
                        "} zset;")
                        .interpretation("zset 结构使用zskiplist ")
                        .interpretation("1: dict用于冗余存储跳表所有节点的分数")
                        .interpretation("2: zsl即这个排序列表使用的跳表结");
        }

        @Trace(
                index= 2,
                originClassName = "server.h",
                function = "typeDefineOfZskipkist",
                introduction = "构建Zskiplist结构"
        )
        public void typeDefineOfZSkiplist(){
                Code.SLICE.source("typedef struct zskiplist {" +
                        "    struct zskiplistNode *header, *tail;" +
                        "    unsigned long length;" +
                        "    int level;" +
                        "} zskiplist;")
                        .interpretation("zskiplist 简要信息汇总，包括 跳表头，跳表尾，跳表的长度，跳表的总共层数");
        }

        @Trace(
                index= 3,
                originClassName = "server.h",
                function = "typeDefineOfZskiplistNode",
                introduction = "skiplist每个节点的结构"
        )
        public void typeDefineOfZskiplistNode(){
                Code.SLICE.source("typedef struct zskiplistNode {" +
                        "    sds ele;" +
                        "    double score;" +
                        "    struct zskiplistNode *backward;" +
                        "    struct zskiplistLevel {" +
                        "        struct zskiplistNode *forward;" +
                        "        unsigned long span;" +
                        "    } level[];" +
                        "} zskiplistNode;")
                        .interpretation("每个节点包含节点的节点的key,分数，节点的后向指针，所在的层级信息，层级信息包含前向指针和层级之间的跨度")
                        .interpretation("1: span 跨度，即同一层的两个元素，前一个元素与后一个元素之间跨过了多少个节点，跨度包含了节点自身和节点的后一个节点，也就是说两个节点间的跨度至少是2，高层次的节点之间跨度最大")
                        .interpretation("2: 从这个节点可以看出，所有的层级结构都是通过数组来存储的，并不是多余的存储了key和score");
        }

        @Trace(
                index= 4,
                originClassName = "t_zset.c",
                function = "int zsetAdd(robj *zobj, double score, sds ele, int *flags, double *newscore) ",
                introduction = "无视排序列表的编码方式，在排序列表中增加一个新的元素或者是更新现有元素的分数"
        )
        public void zsetAdd(){
              //...
              Code.SLICE.source("if (zobj->encoding == OBJ_ENCODING_ZIPLIST) {//..." +
                      " if ((eptr = zzlFind(zobj->ptr,ele,&curscore)) != NULL) {" +
                      "         zobj->ptr = zzlDelete(zobj->ptr,eptr);" +
                      "         zobj->ptr = zzlInsert(zobj->ptr,ele,score);" +
                      " } else if (!xx) {" +
                      "         zobj->ptr = zzlInsert(zobj->ptr,ele,score);" +
                      "         if (zzlLength(zobj->ptr) > server.zset_max_ziplist_entries)" +
                      "             zsetConvert(zobj,OBJ_ENCODING_SKIPLIST);" +
                      "         if (sdslen(ele) > server.zset_max_ziplist_value)" +
                      "             zsetConvert(zobj,OBJ_ENCODING_SKIPLIST);" +
                      " }" +
                      "} else if (zobj->encoding == OBJ_ENCODING_SKIPLIST){" +
                      " de = dictFind(zs->dict,ele);" +
                      " if (de != NULL) {" +
                                "znode = zslUpdateScore(zs->zsl,curscore,ele,score);" +
                      "  } else if (!xx) {" +
                      "          ele = sdsdup(ele);" +
                      "          znode = zslInsert(zs->zsl,score,ele);" +
                      "          serverAssert(dictAdd(zs->dict,ele,&znode->score) == DICT_OK);" +
                      "  }" +
                      "}")
                      .interpretation("根据要存储的排序链表的编码方式，分别存储到对应的结构中")
                      .interpretation("1： 如果刚开始使用的编码方式是ziplist，假设key存在，那么就先删掉原来的key,再插入，否则根据选择的命令，执行插入，新插入的元素如果导致存储的元素超过ziplist限制或者元素本身的长度超过限制就吧ziplist转成skiplist")
                      .interpretation("2: 如果使用的方式是skiplist,假设key存在则执行更新操作，否则拷贝完整的节点key,并将其作为新的节点插入,另外冗余一份 key对应的score放入 dict 结构中");
        }

        @KeyPoint
        @Trace(
                index= 5,
                originClassName = "t_zset.c",
                function = "zskiplistNode *zslInsert(zskiplist *zsl, double score, sds ele)",
                introduction = "插入新的跳表节点"
        )
        public void   zslInsert(){
                //...
                Code.SLICE.source("x = zsl->header;")
                        .interpretation("拿到跳表的头节点");
                Code.SLICE.source("for (i = zsl->level-1; i >= 0; i--) {" +
                        "        /* store rank that is crossed to reach the insert position */" +
                        "        rank[i] = i == (zsl->level-1) ? 0 : rank[i+1];" +
                        "        while (x->level[i].forward &&" +
                        "                (x->level[i].forward->score < score ||" +
                        "                    (x->level[i].forward->score == score &&" +
                        "                    sdscmp(x->level[i].forward->ele,ele) < 0)))" +
                        "        {" +
                        "            rank[i] += x->level[i].span;" +
                        "            x = x->level[i].forward;" +
                        "        }" +
                        "        update[i] = x;" +
                        "    }")
                        .interpretation("从跳表的最高层开始，1层1层的找需要插入的score对应的位置")
                        .interpretation("1: rank用来计算元素在排序列表中排的顺序，rank[i]表示新节点的前一个节点与每层距离头节点的跨度")
                        .interpretation("2: 只要当前节点的分数小于要插入节点的分数，并且当前节点的前头还有，那么就一直往前遍历，记录下来层级之间的跨度，和最后需要插入元素的节点的前一个节点")
                        .interpretation("3: 如果分数一模一样，则比较key,key值大，仍然往前遍历")
                        .interpretation("4: 注意最高层的下标是 level-1");
                Code.SLICE.source("level = zslRandomLevel();")
                        .interpretation("随机产生层级");
                Code.SLICE.source("if (level > zsl->level) {" +
                        "        for (i = zsl->level; i < level; i++) {" +
                        "            rank[i] = 0;" +
                        "            update[i] = zsl->header;" +
                        "            update[i]->level[i].span = zsl->length;" +
                        "        }" +
                        "        zsl->level = level;" +
                        "    }")
                        .interpretation("如果产生的层级大于当前提跳表的最大层级，那么将当前层级置为最高的层级")
                        .interpretation("1: 在所有新增的层都记下头节点和跳表的长度");
                Code.SLICE.source("x = zslCreateNode(level,score,ele);")
                        .interpretation("创建一个跳表的节点对象，作为需要新插入的节点");
                Code.SLICE.source(" for (i = 0; i < level; i++) {" +
                        "        x->level[i].forward = update[i]->level[i].forward;" +
                        "        update[i]->level[i].forward = x;" +
                        "" +
                        "        /* update span covered by update[i] as x is inserted here */" +
                        "        x->level[i].span = update[i]->level[i].span - (rank[0] - rank[i]);" +
                        "        update[i]->level[i].span = (rank[0] - rank[i]) + 1;" +
                        "    }")
                        .interpretation("遍历新元素所在层以及它下面的所有层级，插入新的元素,由于下层是插入新元素，那么这些位置的跨度必然会使得原有跨度变成两半")
                        .interpretation("1: 在遍历的时候已经记下了下面每一层的插入位置的前一个节点，那么新的节点的下一个节点就是已经查找位置的下一个节点，而要插入位置的元素它的下一个节点，就是新插入的节点")
                        .interpretation("2：Rank[0]表示第一层的总共跨度，也就是新元素在跳表中的排序，rank[i]是新节点的前一个节点在每层距离头节点的跨度，在插入新元素之前，前后的总跨度是 update[i]->level[i].span ")
                        .interpretation("3: 假设原有节点的跨度是4，原有两个节点的位置分别是 1和4，假设新插入的位置是 3， rank[0]-rank[i]的值2，那么新节点的跨度就是 4-2=2（2表示新节点和新节点的下一个节点）,位置1的节点的跨度就是 4-2+1=3");
                Code.SLICE.source("/* increment span for untouched levels */" +
                        "    for (i = level; i < zsl->level; i++) {" +
                        "        update[i]->level[i].span++;" +
                        "    }")
                        .interpretation("在新插入层级之上的层级，它们下方由于都新插入了一个节点，那么跨度均加1即可");
                Code.SLICE.source("x->backward = (update[0] == zsl->header) ? NULL : update[0];")
                        .interpretation("如果新插入节点的前一个接单是头节点，则不设置后向指针，否则设置后向指针为它的前一个节点")
                        .interpretation("1 这里可以看到头节点其实只是作为1个指针使用，并不参与存值");
                Code.SLICE.source("if (x->level[0].forward)" +
                        "        x->level[0].forward->backward = x;" +
                        "    else" +
                        "        zsl->tail = x;")
                        .interpretation("如果新节点前面仍然存在节点，那么新节点的前一个节点的后节点就是新节点本身,否则说明新节点就是尾结点")
                        .interpretation("1: 这里可以看出只有第一层才是双向的链表");
                Code.SLICE.source("zsl->length++;" +
                        "    return x;")
                        .interpretation("插入了新的节点x,那么跳表总长度加1，返回新建的节点即可");
        }

    @KeyPoint
    @Trace(
            index= 6,
            originClassName = "t_zset.c",
            function = "int zslRandomLevel(void)",
            introduction = "随机生成新节点应该在的层级"
    )
    public void zslRandomLevel(){
        Code.SLICE.source("int zslRandomLevel(void) {" +
                "    int level = 1;" +
                "    while ((random()&0xFFFF) < (ZSKIPLIST_P * 0xFFFF))" +
                "        level += 1;" +
                "    return (level<ZSKIPLIST_MAXLEVEL) ? level : ZSKIPLIST_MAXLEVEL;" +
                "}")
                .interpretation("产生一个随机数，将它与一个较大数的一半比较，如果一直是小于大数的一半，那么层级就往上走，否则返回已经累计的层级")
                .interpretation("1：最多允许64层");
    }

    @Trace(
            index= 7,
            originClassName = "t_zset.c",
            function = "zskiplistNode *zslUpdateScore(zskiplist *zsl, double curscore, sds ele, double newscore)",
            introduction = "更新节点,显示找到节点再对节点进行操作"
    )
    public void zslUpdateScore(){
        //...
        Code.SLICE.source("x = x->level[0].forward;")
                .interpretation("x即通过一系列查找后找到的要更新节点的位置");
        Code.SLICE.source("if ((x->backward == NULL || x->backward->score < newscore) &&" +
                "        (x->level[0].forward == NULL || x->level[0].forward->score > newscore))" +
                "    {" +
                "        x->score = newscore;" +
                "        return x;" +
                "    }")
                .interpretation("新节点的分数使得不用更改节点的位置，那么只需要更新分数即可");
        Code.SLICE.source("zslDeleteNode(zsl, x, update);" +
                "zskiplistNode *newnode = zslInsert(zsl,newscore,x->ele);")
                .interpretation("分数变更，使得位置发生变化，那么先删除旧的节点，再根据新值插入新的节点，使得跳表的性质得以保存");
        //..
    }

    @Trace(
            index= 8,
            originClassName = "t_zset.c",
            function = "void zslDeleteNode(zskiplist *zsl, zskiplistNode *x, zskiplistNode **update)",
            introduction = "删除节点"
    )
    public void  zslDeleteNode(){
        Code.SLICE.source("for (i = 0; i < zsl->level; i++) {" +
                "        if (update[i]->level[i].forward == x) {" +
                "            update[i]->level[i].span += x->level[i].span - 1;" +
                "            update[i]->level[i].forward = x->level[i].forward;" +
                "        } else {" +
                "            update[i]->level[i].span -= 1;" +
                "        }" +
                "    }")
                .interpretation("从底层开始往上走，找到要删除节点的前一个节点，更换删除节点的前一个节点的下一个节点的指向，修正跨度")
                .interpretation("1: update是要删除节点的前一个节点");
        Code.SLICE.source("if (x->level[0].forward) {" +
                "        x->level[0].forward->backward = x->backward;" +
                "    } else {" +
                "        zsl->tail = x->backward;" +
                "    }")
                .interpretation("如果要删除的节点有前面还有节点，那么更新它的后向指针，否则变更尾结点的指向");
        Code.SLICE.source("while(zsl->level > 1 && zsl->header->level[zsl->level-1].forward == NULL)" +
                "        zsl->level--;")
                .interpretation("如果最高层的头节点已经没有了下一个节点，说明要删除的节点是在最高层，而且这层只有它一个记录数据的节点，那么需要将层级减少");
        Code.SLICE.source("zsl->length--;")
                .interpretation("跳表长度减少1个");
    }










}


