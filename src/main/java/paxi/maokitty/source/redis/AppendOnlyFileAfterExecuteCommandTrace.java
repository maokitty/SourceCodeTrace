/**
 * @(#)AppendOnlyFileTrace.java, 4月 06, 2020.
 * <p>
 * Copyright 2020 fenbi.com. All rights reserved.
 * FENBI.COM PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package paxi.maokitty.source.redis;

import paxi.maokitty.source.annotation.Background;
import paxi.maokitty.source.annotation.KeyPoint;
import paxi.maokitty.source.annotation.Main;
import paxi.maokitty.source.annotation.Trace;
import paxi.maokitty.source.util.Code;

/**
 * @author maokitty
 */
@Background(
        target = "了解用户执行完每个命令后的AOF机制",
        conclusion = "1:每个命令执行完后会按照命令原始的文本被追加到内存中，后续\"等待\"机会再写入磁盘;2: redis会根据配置定时的将数据写入磁盘",
        sourceCodeProjectName = "redis",
        sourceCodeAddress = "https://github.com/antirez/redis",
        projectVersion = "5.0.0"

)
public class AppendOnlyFileAfterExecuteCommandTrace {
    @Trace(
            index = 0,
            originClassName = "server.c",
            function = "void call(client *c, int flags) ",
            more = "flags 在执行命令的入口传入的参数是 CMD_CALL_FULL"
    )
    public void call(){
        //..
        Code.SLICE.source("c->cmd->proc(c);")
                .interpretation("执行收到的命令");
        //
        Code.SLICE.source("propagate(c->cmd,c->db->id,c->argv,c->argc,propagate_flags);")
                .interpretation("将执行的命令传播给AOF或者是从库");
        //...
    }

    @Trace(
            index = 1,
            originClassName = "server.c",
            function = "void propagate(struct redisCommand *cmd, int dbid, robj **argv, int argc,int flags)"
    )
    public void propagate(){
        Code.SLICE.source("if (server.aof_state != AOF_OFF && flags & PROPAGATE_AOF)" +
                "        feedAppendOnlyFile(cmd,dbid,argv,argc);")
                .interpretation("需要执行AOF，那么就执行");
        //..
    }

    @Main
    @Trace(
            index = 2,
            originClassName = "aof.c",
            function = "void feedAppendOnlyFile(struct redisCommand *cmd, int dictid, robj **argv, int argc)"
    )
    public void feedAppendOnlyFile(){
        //...
        Code.SLICE.source("if (cmd->proc == expireCommand || cmd->proc == pexpireCommand ||" +
                "        cmd->proc == expireatCommand) {" +
                "        /* Translate EXPIRE/PEXPIRE/EXPIREAT into PEXPIREAT */" +
                "        buf = catAppendOnlyExpireAtCommand(buf,cmd,argv[1],argv[2]);" +
                "    } else if (cmd->proc == setexCommand || cmd->proc == psetexCommand) {" +
                "        /* Translate SETEX/PSETEX to SET and PEXPIREAT */" +
                "        tmpargv[0] = createStringObject(\"SET\",3);" +
                "        tmpargv[1] = argv[1];" +
                "        tmpargv[2] = argv[3];" +
                "        buf = catAppendOnlyGenericCommand(buf,3,tmpargv);" +
                "        decrRefCount(tmpargv[0]);" +
                "        buf = catAppendOnlyExpireAtCommand(buf,cmd,argv[1],argv[2]);" +
                "    } else if (cmd->proc == setCommand && argc > 3) {" +
                "        int i;" +
                "        robj *exarg = NULL, *pxarg = NULL;" +
                "        /* Translate SET [EX seconds][PX milliseconds] to SET and PEXPIREAT */" +
                "        buf = catAppendOnlyGenericCommand(buf,3,argv);" +
                "        for (i = 3; i < argc; i ++) {" +
                "            if (!strcasecmp(argv[i]->ptr, \"ex\")) exarg = argv[i+1];" +
                "            if (!strcasecmp(argv[i]->ptr, \"px\")) pxarg = argv[i+1];" +
                "        }" +
                "        serverAssert(!(exarg && pxarg));" +
                "        if (exarg)" +
                "            buf = catAppendOnlyExpireAtCommand(buf,server.expireCommand,argv[1]," +
                "                                               exarg);" +
                "        if (pxarg)" +
                "            buf = catAppendOnlyExpireAtCommand(buf,server.pexpireCommand,argv[1]," +
                "                                               pxarg);" +
                "    } else {" +
                "        /* All the other commands don't need translation or need the" +
                "         * same translation already operated in the command vector" +
                "         * for the replication itself. */" +
                "        buf = catAppendOnlyGenericCommand(buf,argc,argv);" +
                "    }")
                .interpretation("根据要执行的命令，转化成AOF对应的协议，存储到AOF中")
                .interpretation("1: EXPIRE/PEXPIRE/EXPIREAT 转成 PEXPIREAT  ")
                .interpretation("2: SETEX/PSETEX 转成 SET 和 PEXPIREAT")
                .interpretation("3: SET [EX seconds][PX milliseconds] 转成 SET 和 PEXPIREAT");
        Code.SLICE.source("if (server.aof_state == AOF_ON)" +
                "        server.aof_buf = sdscatlen(server.aof_buf,buf,sdslen(buf));")
                .interpretation("AOF模式开启，那么，将命令写入AOF_BUF");
        Code.SLICE.source("if (server.aof_child_pid != -1)" +
                "        aofRewriteBufferAppend((unsigned char*)buf,sdslen(buf));")
                .interpretation("1: 如果正在进行aof重写，那么新的命令也会被同步到aof rewrite的buf中,底层通过双向链表来先暂时存储新的命令");
        //...
    }

    @Trace(
            index = 3,
            originClassName = "aof.c",
            function = "sds catAppendOnlyExpireAtCommand(sds buf, struct redisCommand *cmd, robj *key, robj *seconds)"
    )
    public void catAppendOnlyExpireAtCommand(){
        //...
        Code.SLICE.source("when = strtoll(seconds->ptr,NULL,10);")
                .interpretation("拿到要过期的秒数");
        Code.SLICE.source("/* Convert argument into milliseconds for EXPIRE, SETEX, EXPIREAT */" +
                "    if (cmd->proc == expireCommand || cmd->proc == setexCommand ||" +
                "        cmd->proc == expireatCommand)" +
                "    {" +
                "        when *= 1000;" +
                "    }" +
                "    /* Convert into absolute time for EXPIRE, PEXPIRE, SETEX, PSETEX */" +
                "    if (cmd->proc == expireCommand || cmd->proc == pexpireCommand ||" +
                "        cmd->proc == setexCommand || cmd->proc == psetexCommand)" +
                "    {" +
                "        when += mstime();" +
                "    }")
                .interpretation("将过期时间换成最终能存活到的时间");
        //..
        Code.SLICE.source("argv[0] = createStringObject(\"PEXPIREAT\",9);" +
                "    argv[1] = key;" +
                "    argv[2] = createStringObjectFromLongLong(when);" +
                "    buf = catAppendOnlyGenericCommand(buf, 3, argv);")
                .interpretation(" 创建PEXPIREAT 命令再转转成对应的AOF格式进行存储 ");
        //..
    }

    @KeyPoint
    @Trace(
            index = 5,
            originClassName = "aof.c",
            function = "sds catAppendOnlyGenericCommand(sds dst, int argc, robj **argv) "
    )
    public void catAppendOnlyGenericCommand(){
        Code.SLICE.source("buf[0] = '*';" +
                "    len = 1+ll2string(buf+1,sizeof(buf)-1,argc);" +
                "    buf[len++] = '\r';" +
                "    buf[len++] = '\n';" +
                "    dst = sdscatlen(dst,buf,len);" +
                "    for (j = 0; j < argc; j++) {" +
                "        o = getDecodedObject(argv[j]);" +
                "        buf[0] = '$';" +
                "        len = 1+ll2string(buf+1,sizeof(buf)-1,sdslen(o->ptr));" +
                "        buf[len++] = '\r';" +
                "        buf[len++] = '\n';" +
                "        dst = sdscatlen(dst,buf,len);" +
                "        dst = sdscatlen(dst,o->ptr,sdslen(o->ptr));" +
                "        dst = sdscatlen(dst,\"\r\n\",2);" +
                "        decrRefCount(o);" +
                "    }")
                .interpretation("按照一定的格式转化命令，放到要存放的目的地")
                .interpretation("1: 先计算命令一共有几个字符 ，比如命令 set msg hello ，那么首先写入的就是 3，然后追加 \r\n")
                .interpretation("2: 遍历每个字符，先写下$符号，然后记下这个字符的长度，追加 \r\n再记下长度和字符本身，然后追加 \r\n")
                .interpretation("3: set msg hello 最终需要追加的内容为  3\r\n$3\r\nmsg\r\n$5\r\nhello\r\n");
    }

    @Trace(
            index = 6,
            originClassName = "aof.c",
            function = "void aofRewriteBufferAppend(unsigned char *s, unsigned long len) "
    )
    public void aofRewriteBufferAppend(){
        Code.SLICE.source("listNode *ln = listLast(server.aof_rewrite_buf_blocks);")
                .interpretation("获取aof重写缓冲区的最后一个节点，从结构可以看出aof的重写缓冲区是一个双向链表");
        //..
        Code.SLICE.source("if (block) {" +
                "            unsigned long thislen = (block->free < len) ? block->free : len;" +
                "            if (thislen) {  /* The current block is not already full. */" +
                "                memcpy(block->buf+block->used, s, thislen);" +
                "                block->used += thislen;" +
                "                block->free -= thislen;" +
                "                s += thislen;" +
                "                len -= thislen;" +
                "            }" +
                "        }")
                .interpretation("如果最后一个节点还有空余的空间，在这里面写入能够写入的长度");
        //..
        Code.SLICE.source("if (len) { /* First block to allocate, or need another block. */" +
                "            int numblocks;" +
                "            block = zmalloc(sizeof(*block));" +
                "            block->free = AOF_RW_BUF_BLOCK_SIZE;" +
                "            block->used = 0;" +
                "            listAddNodeTail(server.aof_rewrite_buf_blocks,block);" +
                "            /* Log every time we cross more 10 or 100 blocks, respectively" +
                "             * as a notice or warning. */" +
                "            numblocks = listLength(server.aof_rewrite_buf_blocks);" +
                "            if (((numblocks+1) % 10) == 0) {" +
                "                int level = ((numblocks+1) % 100) == 0 ? LL_WARNING :" +
                "                                                         LL_NOTICE;" +
                "                serverLog(level,\"Background AOF buffer size: %lu MB\"," +
                "                    aofRewriteBufferSize()/(1024*1024));" +
                "            }" +
                "        }")
                .interpretation("剩余的数据需要的空间再单独申请，追加到队列尾部");
        //..
        Code.SLICE.source("if (aeGetFileEvents(server.el,server.aof_pipe_write_data_to_child) == 0) {" +
                "        aeCreateFileEvent(server.el, server.aof_pipe_write_data_to_child," +
                "            AE_WRITABLE, aofChildWriteDiffData, NULL);" +
                "    }")
                .interpretation("如果当前没有重写的管道，就触发建立buf重写的事件,以便数据能送给正在重写的子进程");
    }

    @Trace(
            index = 7,
            originClassName = "aof.c",
            function = "void aofChildWriteDiffData(aeEventLoop *el, int fd, void *privdata, int mask) ",
            more = "在执行aof缓冲区的追加过程中，会通过事件触发的机制最终触发这个函数的执行，省略事件触发机制，直接执行这里;在执行rewrite的地方会进行读取，详见BgRewriteAofCommandExecuteTrace"
    )
    public void aofChildWriteDiffData(){
        //...
        Code.SLICE.source("while(1) {" +
                "        ln = listFirst(server.aof_rewrite_buf_blocks);" +
                "        block = ln ? ln->value : NULL;" +
                "        if (server.aof_stop_sending_diff || !block) {" +
                "            aeDeleteFileEvent(server.el,server.aof_pipe_write_data_to_child," +
                "                              AE_WRITABLE);" +
                "            return;" +
                "        }" +
                "        if (block->used > 0) {" +
                "            nwritten = write(server.aof_pipe_write_data_to_child," +
                "                             block->buf,block->used);" +
                "            if (nwritten <= 0) return;" +
                "            memmove(block->buf,block->buf+nwritten,block->used-nwritten);" +
                "            block->used -= nwritten;" +
                "            block->free += nwritten;" +
                "        }" +
                "        if (block->used == 0) listDelNode(server.aof_rewrite_buf_blocks,ln);" +
                "    }")
                .interpretation("从双向链表存储的第一个节点开始，一直读取数据写入正在执行aof rewrite的子进程，直到没有数据或者读到了暂停写的标记");
    }

    @Trace(
            index = 8,
            originClassName = "server.c",
            function = "int serverCron(struct aeEventLoop *eventLoop, long long id, void *clientData)",
            more = "redis会每秒调用这个函数 server.hz 次"
    )
    public void serverCron(){
        //..
        Code.SLICE.source("if (hasActiveChildProcess() || ldbPendingChildren())" +
                "    {" +
                "        checkChildrenDone();" +
                "    }else{" +
                "        //..." +
                "        /* Trigger an AOF rewrite if needed. */" +
                "        if (server.aof_state == AOF_ON &&" +
                "            !hasActiveChildProcess() &&" +
                "            server.aof_rewrite_perc &&" +
                "            server.aof_current_size > server.aof_rewrite_min_size)" +
                "        {" +
                "            long long base = server.aof_rewrite_base_size ?" +
                "                server.aof_rewrite_base_size : 1;" +
                "            long long growth = (server.aof_current_size*100/base) - 100;" +
                "            if (growth >= server.aof_rewrite_perc) {" +
                "                serverLog(LL_NOTICE,\"Starting automatic rewriting of AOF on %lld%% growth\",growth);" +
                "                rewriteAppendOnlyFileBackground();" +
                "            }" +
                "        }" +
                "} ")
                .interpretation("如果aof的子进程或者RDB的子进程存在，后台检查，这个过程是否完成，如果没有就检查是否需要触发AOF重写");

        Code.SLICE.source("/* AOF postponed flush: Try at every cron cycle if the slow fsync" +
                "     * completed. */" +
                "    if (server.aof_flush_postponed_start) flushAppendOnlyFile(0);" +
                "    /* AOF write errors: in this case we have a buffer to flush as well and" +
                "     * clear the AOF error in case of success to make the DB writable again," +
                "     * however to try every second is enough in case of 'hz' is set to" +
                "     * an higher frequency. */" +
                "    run_with_period(1000) {" +
                "        if (server.aof_last_write_status == C_ERR)" +
                "            flushAppendOnlyFile(0);" +
                "    }")
                .interpretation("只要AOF flush推迟标志不是0，就执行一次flushAppendOnlyFile,并每秒检查1次，是否执行出错，有就再执行");
    }

    @Trace(
            index = 9,
            originClassName = "aof.c",
            function = "void flushAppendOnlyFile(int force) "
    )
    public void  flushAppendOnlyFile(){
        //...
        Code.SLICE.source("if (sdslen(server.aof_buf) == 0) {" +
                "        /* Check if we need to do fsync even the aof buffer is empty," +
                "         * because previously in AOF_FSYNC_EVERYSEC mode, fsync is" +
                "         * called only when aof buffer is not empty, so if users" +
                "         * stop write commands before fsync called in one second," +
                "         * the data in page cache cannot be flushed in time. */" +
                "        if (server.aof_fsync == AOF_FSYNC_EVERYSEC &&" +
                "            server.aof_fsync_offset != server.aof_current_size &&" +
                "            server.unixtime > server.aof_last_fsync &&" +
                "            !(sync_in_progress = aofFsyncInProgress())) {" +
                "            goto try_fsync;" +
                "        } else {" +
                "            return;" +
                "        }" +
                "    }")
                .interpretation("如果aof_buf缓冲区没有数据，先检查是否需要再执行一次  fsync,将数据写入磁盘，否则直接返回");

        Code.SLICE.source(" if (server.aof_fsync == AOF_FSYNC_EVERYSEC)" +
                "        sync_in_progress = aofFsyncInProgress();")
                .interpretation("如果配置每秒执行1次的 aof_fsync,先读取正在进行的 fsync 的数量");

        Code.SLICE.source("if (server.aof_fsync == AOF_FSYNC_EVERYSEC && !force) {" +
                "        /* With this append fsync policy we do background fsyncing." +
                "         * If the fsync is still in progress we can try to delay" +
                "         * the write for a couple of seconds. */" +
                "        if (sync_in_progress) {" +
                "            if (server.aof_flush_postponed_start == 0) {" +
                "                /* No previous write postponing, remember that we are" +
                "                 * postponing the flush and return. */" +
                "                server.aof_flush_postponed_start = server.unixtime;" +
                "                return;" +
                "            } else if (server.unixtime - server.aof_flush_postponed_start < 2) {" +
                "                /* We were already waiting for fsync to finish, but for less" +
                "                 * than two seconds this is still ok. Postpone again. */" +
                "                return;" +
                "            }" +
                "            /* Otherwise fall trough, and go write since we can't wait" +
                "             * over two seconds. */" +
                "            server.aof_delayed_fsync++;" +
                "            serverLog(LL_NOTICE,\"Asynchronous AOF fsync is taking too long (disk is busy?). Writing the AOF buffer without waiting for fsync to complete, this may slow down Redis.\");" +
                "        }" +
                "    }")
                .interpretation("如果是每秒钟进行同步磁盘,并且非强制刷新内存数据到磁盘，且有正在执行 fsync ")
                .interpretation("1: 如果本次执行之前，没有执行过推迟，设置当前时间为首次进行推迟的时间，直接返回")
                .interpretation("2：如果之前已经执行过推迟，并且时间间隔小于2秒，再次推迟,此时就算fsync还没结束，这种时间是可接受的")
                .interpretation("3: 推迟时间超过2米哦按，记录日志，延迟次数增1，继续往下走");

        Code.SLICE.source("if (server.aof_flush_sleep && sdslen(server.aof_buf)) {" +
                "        usleep(server.aof_flush_sleep);" + "    }")
                .interpretation("如果配置了同步磁盘休眠，并且aof缓冲区有数据，那么执行进程挂起一段时间");
        //..
        Code.SLICE.source("nwritten = aofWrite(server.aof_fd,server.aof_buf,sdslen(server.aof_buf));")
                .interpretation("将aof缓冲区的数据写入磁盘")
        .interpretation("1: aof_fd 是在启动的时候，如果开启了 AOF，它就会拿到 AOF文件的文件描述符；或者是用户使用 config 命令开启 append-only 获取 AOF文件 ");
        //..
        Code.SLICE.source("server.aof_flush_postponed_start = 0;")
                .interpretation("已经执行过写入磁盘，将延迟标记记为0");
        //..
        Code.SLICE.source("if (nwritten != (ssize_t)sdslen(server.aof_buf)) ")
                .interpretation("如果没有全部写入，记录错误日志，否则标记写入成功");
        //..
        Code.SLICE.source("server.aof_current_size += nwritten;" +
                "    /* Re-use AOF buffer when it is small enough. The maximum comes from the" +
                "     * arena size of 4k minus some overhead (but is otherwise arbitrary). */" +
                "    if ((sdslen(server.aof_buf)+sdsavail(server.aof_buf)) < 4000) {" +
                "        sdsclear(server.aof_buf);" +
                "    } else {" +
                "        sdsfree(server.aof_buf);" +
                "        server.aof_buf = sdsempty();" +
                "    }")
                .interpretation("记录下aof当前的大小，清空aof_buf的历史数据");
        //以下是try_fsync逻辑简述
        //..
        Code.SLICE.source("redis_fsync(server.aof_fd); /* Let's try to get this data on the disk */")
                .interpretation("如果是一直执行fsync,执行一次fsync的命令,对于linux而言就是 fdatasync ，其余就是执行 fsync");
        //..
        Code.SLICE.source("if (!sync_in_progress) {" +
                "            aof_background_fsync(server.aof_fd);" +
                "            server.aof_fsync_offset = server.aof_current_size;" +
                "        }")
                .interpretation("如果是每秒执行fsync,当前时间大于上次执行时间，并且没有执行中的fsync,则在阻塞进程中创建1个fsync事件，等待后台执行这个命令");

    }

    @Trace(
            index = 10,
            originClassName = "server.c",
            function = "void checkChildrenDone(void)  "
    )
    public void checkChildrenDone(){
        //..
        Code.SLICE.source("if ((pid = wait3(&statloc,WNOHANG,NULL)) != 0){" +
                "        //..." +
                "           else if (pid == server.rdb_child_pid) {\n" +
                "            backgroundSaveDoneHandler(exitcode,bysignal);" +
                "            if (!bysignal && exitcode == 0) receiveChildInfo();" +
                "        } else if (pid == server.aof_child_pid) {" +
                "            backgroundRewriteDoneHandler(exitcode,bysignal);" +
                "            if (!bysignal && exitcode == 0) receiveChildInfo();" +
                "        } " +
                "        //.." +
                "}")
                .interpretation("拿到有退出执行的子进程的PID，如果存在退出的子进程就执行逻辑")
                .interpretation("1: 这里调用系统命令，WNOHANG 表示就算没有子进程也返回")
                .interpretation("2: 如果退出的进程ID是rdb的子进程，就执行rdb需要做的事情")
                .interpretation("3：如果退出的进程ID是aof的子进程 ，就执行aof子进程完成需要做的事情");

    }

    @Trace(
            index = 11,
            originClassName = "aof.c",
            function = "void backgroundRewriteDoneHandler(int exitcode, int bysignal) "
    )
    public void  backgroundRewriteDoneHandler(){
        Code.SLICE.source("char tmpfile[256];" +
                "        //..." +
                "        snprintf(tmpfile,256,\"temp-rewriteaof-bg-%d.aof\"," +
                "            (int)server.aof_child_pid);" +
                "        newfd = open(tmpfile,O_WRONLY|O_APPEND);")
                .interpretation("拿到AOF子进程的临时文件,打开");
        //..
        Code.SLICE.source("if (aofRewriteBufferWrite(newfd) == -1)")
                .interpretation("将 aof_rewrite_buf_blocks 中剩余的数据全部写入文件临时文件中");
        //..
        Code.SLICE.source("if (server.aof_fd == -1) {" +
                "            /* AOF disabled */" +
                "            /* Don't care if this fails: oldfd will be -1 and we handle that." +
                "             * One notable case of -1 return is if the old file does" +
                "             * not exist. */" +
                "            oldfd = open(server.aof_filename,O_RDONLY|O_NONBLOCK);" +
                "        } else {" +
                "            /* AOF enabled */" +
                "            oldfd = -1; /* We'll set this to the current AOF filedes later. */" +
                "        }")
                .interpretation("持有1个旧的AOF文件描述符oldfd，如果一开始的时候 AOF 文件是关闭的，那么仍然打开这个 AOF 文件，其他情况已经开启，默认为-1")
                .interpretation("1: 如果AOF当前是禁止的，但是在之前这个文件却存在过，那么执行 AOF重写的临时文件重命名到 AOF文件时，旧文件会被取消链接，这有可能造成阻塞服务器")
                .interpretation("2: 如果当前AOF是开启的，那么执行 AOF重写的临时文件重命名到 AOF文件时，会将旧的AOF 文件关闭，由于这里是原 AOF 的最后一个引用，那么旧文件会被取消链接，这有可能造成阻塞服务器")
                .interpretation("3: 为了减轻1和2带来的影响，使用后台线程来处理，对于情况1，也就使用了 oldfd 来持有引用；这样情况1也就转成了情况2，而交给别的线程处理之后，这已经在rename之后发生，它的阻塞也就不需要关心了")
                ;
        //..
        Code.SLICE.source("if (rename(tmpfile,server.aof_filename) == -1) ")
                .interpretation("将AOF的临时文件重命名到 AOF 的正式文件名");
        //..
        Code.SLICE.source("if (server.aof_fd == -1) {" +
                "            /* AOF disabled, we don't need to set the AOF file descriptor" +
                "             * to this new file, so we can close it. */" +
                "            close(newfd);" +
                "        } else {" +
                "            /* AOF enabled, replace the old fd with the new one. */" +
                "            oldfd = server.aof_fd;" +
                "            server.aof_fd = newfd;" +
                "            if (server.aof_fsync == AOF_FSYNC_ALWAYS)" +
                "                redis_fsync(newfd);" +
                "            else if (server.aof_fsync == AOF_FSYNC_EVERYSEC)" +
                "                aof_background_fsync(newfd);" +
                "            server.aof_selected_db = -1; /* Make sure SELECT is re-issued */" +
                "            aofUpdateCurrentSize();" +
                "            server.aof_rewrite_base_size = server.aof_current_size;" +
                "            server.aof_fsync_offset = server.aof_current_size;" + "" +
                "            /* Clear regular AOF buffer since its contents was just written to" +
                "             * the new AOF from the background rewrite buffer. */" +
                "            sdsfree(server.aof_buf);" +
                "            server.aof_buf = sdsempty();" +
                "        }")
                .interpretation("如果之前就是没有开启 AOF，那么继续维持关闭即可，否则更换新的文件描述符为AOF的")
                .interpretation("1: 按照 AOF fsync 的配置执行 fsync")
                .interpretation("2: 记下这次重写AOF执行后AOF的大小作为 AOF自动重写的基准")
                .interpretation("3：记下fsync的偏移量")
                ;
        Code.SLICE.source("server.aof_lastbgrewrite_status = C_OK;")
                .interpretation("标记这次重写成功");
        //..
        Code.SLICE.source("if (oldfd != -1) bioCreateBackgroundJob(BIO_CLOSE_FILE,(void*)(long)oldfd,NULL,NULL);")
                .interpretation("异步关闭旧的文件");
        //..
        Code.SLICE.source("aofClosePipes();" +
                "    aofRewriteBufferReset();" +
                "    aofRemoveTempFile(server.aof_child_pid);" +
                "    server.aof_child_pid = -1;" +
                "    server.aof_rewrite_time_last = time(NULL)-server.aof_rewrite_time_start;" +
                "    server.aof_rewrite_time_start = -1;" +
                "    /* Schedule a new rewrite if we are waiting for it to switch the AOF ON. */" +
                "    if (server.aof_state == AOF_WAIT_REWRITE)" +
                "        server.aof_rewrite_scheduled = 1;")
                .interpretation("做AOF重写最后的清理工作做");
    }
}
