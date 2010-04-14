/*
 * $Id:HimeraNfsUtils.java 140 2007-06-07 13:44:55Z tigran $
 */

package org.dcache.chimera.nfs.v3;

import org.dcache.chimera.nfs.v3.xdr.specdata3;
import org.dcache.chimera.nfs.v3.xdr.nfsstat3;
import org.dcache.chimera.nfs.v3.xdr.uint64;
import org.dcache.chimera.nfs.v3.xdr.uid3;
import org.dcache.chimera.nfs.v3.xdr.gid3;
import org.dcache.chimera.nfs.v3.xdr.fattr3;
import org.dcache.chimera.nfs.v3.xdr.nfstime3;
import org.dcache.chimera.nfs.v3.xdr.fileid3;
import org.dcache.chimera.nfs.v3.xdr.uint32;
import org.dcache.chimera.nfs.v3.xdr.mode3;
import org.dcache.chimera.nfs.v3.xdr.sattr3;
import org.dcache.chimera.nfs.v3.xdr.ftype3;
import org.dcache.chimera.nfs.v3.xdr.size3;
import org.dcache.chimera.nfs.v3.xdr.time_how;
import org.dcache.chimera.nfs.v3.xdr.wcc_attr;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import org.dcache.chimera.FsInode;
import org.dcache.chimera.ChimeraFsException;
import org.dcache.chimera.UnixPermission;
import org.dcache.chimera.nfs.v3.xdr.post_op_attr;
import org.dcache.chimera.nfs.v3.xdr.pre_op_attr;
import org.dcache.chimera.nfs.v3.xdr.wcc_data;
import org.dcache.xdr.RpcAuthType;
import org.dcache.xdr.RpcAuthTypeUnix;
import org.dcache.xdr.RpcCall;


public class HimeraNfsUtils {


	private static final int MODE_MASK = 0770000;

	private static final Logger _log = Logger.getLogger(HimeraNfsUtils.class.getName());

    private HimeraNfsUtils() {
        // no instance allowed
    }

    public static void fill_attributes(org.dcache.chimera.posix.Stat stat,  fattr3 at) {

        at.type = unixType2NFS(stat.getMode());
        at.mode = new mode3(new uint32( stat.getMode()  & 0777777 ) );

        //public int nlink;
        at.nlink= new uint32( stat.getNlink() );

        //public int uid;
        at.uid= new uid3( new uint32(stat.getUid()) );

        //public int gid;
        at.gid=new gid3(new uint32( stat.getGid()) );

        //public int rdev;
        at.rdev = new specdata3();
        at.rdev.specdata1 = new uint32(19);  		// ARBITRARY
        at.rdev.specdata2 = new uint32(17);
        //public int blocks;

        //public int fsid;
        at.fsid= new uint64( stat.getDev() );

        //public int fileid;
        // Get some value for this file/dir
        at.fileid = new fileid3(new uint64( stat.getIno() ) );

        at.size = new size3( new uint64( stat.getSize() ) );
        at.used = new size3( new uint64( stat.getSize() ) );

        //public nfstime atime;
        at.atime = new nfstime3();
        at.atime.seconds = new uint32( (int)TimeUnit.SECONDS.convert(stat.getATime() , TimeUnit.MILLISECONDS) );
        at.atime.nseconds = new uint32(0);
        //public nfstime mtime;
        at.mtime = new nfstime3();
        at.mtime.seconds = new uint32( (int)TimeUnit.SECONDS.convert(stat.getMTime() , TimeUnit.MILLISECONDS) );
        at.mtime.nseconds = new uint32(0);
        //public nfstime ctime;
        at.ctime = new nfstime3();
        at.ctime.seconds = new uint32( (int)TimeUnit.SECONDS.convert(stat.getCTime() , TimeUnit.MILLISECONDS) );
        at.ctime.nseconds = new uint32(0);
    }


    public static void fill_attributes(org.dcache.chimera.posix.Stat stat,  wcc_attr at) {

        at.size = new size3( new uint64( stat.getSize() ) );
        //public nfstime mtime;
        at.mtime = new nfstime3();
        at.mtime.seconds = new uint32( (int)TimeUnit.SECONDS.convert(stat.getMTime() , TimeUnit.MILLISECONDS) );
        at.mtime.nseconds = new uint32(0);
        //public nfstime ctime;
        at.ctime = new nfstime3();
        at.ctime.seconds = new uint32( (int)TimeUnit.SECONDS.convert(stat.getCTime() , TimeUnit.MILLISECONDS) );
        at.ctime.nseconds = new uint32(0);

    }

    public static void set_sattr( FsInode inode, sattr3 s) throws ChimeraFsException {

        long now = System.currentTimeMillis();

        if( s.uid.set_it ) {
            inode.setUID( s.uid.uid.value.value);
        }

        if( s.gid.set_it ) {
            inode.setGID(s.gid.gid.value.value);
        }

        if( s.mode.set_it  ) {
            _log.debug("New mode [" + Integer.toOctalString(s.mode.mode.value.value) + "]");
            inode.setMode( s.mode.mode.value.value);
        }

        if( s.size.set_it ) {
            inode.setSize( s.size.size.value.value);
        }

   /*     switch( s.atime.set_it ) {

            case time_how.SET_TO_SERVER_TIME:
            	inode.setATime( System.currentTimeMillis()/1000 );
                break;
            case time_how.SET_TO_CLIENT_TIME:
            	inode.setATime(  (long) s.atime.atime.seconds.value );
                break;
            default:
        } */

        switch( s.mtime.set_it ) {

            case time_how.SET_TO_SERVER_TIME:
            	inode.setMTime( now );
                break;
            case time_how.SET_TO_CLIENT_TIME:
                // update mtime only if it's more than 10 seconds
                long mtime =  TimeUnit.MILLISECONDS.convert(s.mtime.mtime.seconds.value , TimeUnit.SECONDS)  +
                	TimeUnit.MILLISECONDS.convert(s.mtime.mtime.nseconds.value , TimeUnit.NANOSECONDS);
                inode.setMTime(  mtime );
                break;
            default:
        }

    }


    static int unixType2NFS( int type ) {

        int ret = 0;

        switch ( type & MODE_MASK  ) {

            case UnixPermission.S_IFREG:
                ret = ftype3.NF3REG;
                break;
            case UnixPermission.S_IFDIR:
                ret = ftype3.NF3DIR;
                break;
            case UnixPermission.S_IFLNK:
                ret = ftype3.NF3LNK;
                break;
            case UnixPermission.S_IFSOCK:
                ret = ftype3.NF3SOCK;
                break;
            case UnixPermission.S_IFBLK:
                ret = ftype3.NF3BLK;
                break;
            case UnixPermission.S_IFCHR:
                ret = ftype3.NF3CHR;
                break;
            case UnixPermission.S_IFIFO:
                ret = ftype3.NF3FIFO;
                break;
            default:
                _log.info("Unknown mode [" + Integer.toOctalString(type) +"]");
                ret = 0;
        }

        return ret;
    }

    public static org.dcache.chimera.posix.UnixUser remoteUser(RpcCall call, boolean isTrusted) {

        org.dcache.chimera.posix.UnixUser user = null;
        int uid = -1;
        int gid = -1;
        int[] gids = null;

        if( call.getCredential().type() == RpcAuthType.UNIX) {

            uid = ((RpcAuthTypeUnix)call.getCredential()).uid();
            gid = ((RpcAuthTypeUnix)call.getCredential()).gid();
            gids = ((RpcAuthTypeUnix)call.getCredential()).gids();
        }


        String host = call.getTransport().getRemoteSocketAddress().getAddress().getHostName();


        // root access only for trusted hosts
        if( uid == 0 && ! isTrusted) {
            // FIXME: actually 'nobody' account should be used
            uid = -1;
            gid = -1;
        }

        user = new org.dcache.chimera.posix.UnixUser(uid, gid, gids, host);

        return user;
    }

    static public String nfsErr2String(int nfsStatus) {

        String ret = null;

        switch (nfsStatus) {
            case nfsstat3.NFS3_OK:
                ret = "NFS3_OK";
                break;
            case nfsstat3.NFS3ERR_PERM:
                ret = "NFS3ERR_PERM";
                break;
            case nfsstat3.NFS3ERR_NOENT:
                ret = "NFS3ERR_NOENT";
                break;
            case nfsstat3.NFS3ERR_IO:
                ret = "NFS3ERR_IO";
                break;
            case nfsstat3.NFS3ERR_NXIO:
                ret = "NFS3ERR_NXIO";
                break;
            case nfsstat3.NFS3ERR_ACCES:
                ret = "NFS3ERR_ACCES";
                break;
            case nfsstat3.NFS3ERR_EXIST:
                ret = "NFS3ERR_EXIST";
                break;
            case nfsstat3.NFS3ERR_XDEV:
                ret = "NFS3ERR_XDEV";
                break;
            case nfsstat3.NFS3ERR_NODEV:
                ret = "NFS3ERR_NODEV";
                break;
            case nfsstat3.NFS3ERR_NOTDIR:
                ret = "NFS3ERR_NOTDIR";
                break;
            case nfsstat3.NFS3ERR_ISDIR:
                ret = "NFS3ERR_ISDIR";
                break;
            case nfsstat3.NFS3ERR_INVAL:
                ret = "NFS3ERR_INVAL";
                break;
            case nfsstat3.NFS3ERR_FBIG:
                ret = "NFS3ERR_FBIG";
                break;
            case nfsstat3.NFS3ERR_NOSPC:
                ret = "NFS3ERR_NOSPC";
                break;
            case nfsstat3.NFS3ERR_ROFS:
                ret = "NFS3ERR_ROFS";
                break;
            case nfsstat3.NFS3ERR_MLINK:
                ret = "NFS3ERR_MLINK";
                break;
            case nfsstat3.NFS3ERR_NAMETOOLONG:
                ret = "NFS3ERR_NAMETOOLONG";
                break;
            case nfsstat3.NFS3ERR_NOTEMPTY:
                ret = "NFS3ERR_NOTEMPTY";
                break;
            case nfsstat3.NFS3ERR_DQUOT:
                ret = "NFS3ERR_DQUOT";
                break;
            case nfsstat3.NFS3ERR_STALE:
                ret = "NFS3ERR_STALE";
                break;
            case nfsstat3.NFS3ERR_REMOTE:
                ret = "NFS3ERR_REMOTE";
                break;
            case nfsstat3.NFS3ERR_BADHANDLE:
                ret = "NFS3ERR_BADHANDLE";
                break;
            case nfsstat3.NFS3ERR_NOT_SYNC:
                ret = "NFS3ERR_NOT_SYNC";
                break;
            case nfsstat3.NFS3ERR_BAD_COOKIE:
                ret = "NFS3ERR_BAD_COOKIE";
                break;
            case nfsstat3.NFS3ERR_NOTSUPP:
                ret = "NFS3ERR_NOTSUPP";
                break;
            case nfsstat3.NFS3ERR_TOOSMALL:
                ret = "NFS3ERR_TOOSMALL";
                break;
            case nfsstat3.NFS3ERR_SERVERFAULT:
                ret = "NFS3ERR_SERVERFAULT";
                break;
            case nfsstat3.NFS3ERR_BADTYPE:
                ret = "NFS3ERR_BADTYPE";
                break;
            case nfsstat3.NFS3ERR_JUKEBOX:
                ret = "NFS3ERR_JUKEBOX";
                break;
        }
        return ret;

    }

    /**
     * Create empty post operational attributes.
     * @return attrs
     */
    public static post_op_attr defaultPostOpAttr() {
        post_op_attr postOpAttr = new post_op_attr();
        postOpAttr.attributes_follow = false;
        return postOpAttr;
    }

    /**
     * Create empty pre operational attributes;
     * @return attrs
     */
    public static pre_op_attr defaultPreOpAttr() {
        pre_op_attr preOpAttr = new pre_op_attr();
        preOpAttr.attributes_follow = false;
        return preOpAttr;
    }

    /**
     * Create empty weak cache consistency information.
     * @return cache entry
     */
    public static wcc_data defaultWccData() {
        wcc_data wccData = new wcc_data();
        wccData.after = defaultPostOpAttr();
        wccData.before = defaultPreOpAttr();
        return wccData;
    }
}