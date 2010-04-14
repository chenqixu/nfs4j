package org.dcache.chimera.nfs.v4;

import org.dcache.chimera.nfs.v4.xdr.open_delegation_type4;
import org.dcache.chimera.nfs.v4.xdr.change_info4;
import org.dcache.chimera.nfs.v4.xdr.bitmap4;
import org.dcache.chimera.nfs.v4.xdr.nfs4_prot;
import org.dcache.chimera.nfs.v4.xdr.nfs_argop4;
import org.dcache.chimera.nfs.v4.xdr.changeid4;
import org.dcache.chimera.nfs.v4.xdr.nfsstat4;
import org.dcache.chimera.nfs.v4.xdr.uint32_t;
import org.dcache.chimera.nfs.v4.xdr.opentype4;
import org.dcache.chimera.nfs.v4.xdr.open_claim_type4;
import org.dcache.chimera.nfs.v4.xdr.open_delegation4;
import org.dcache.chimera.nfs.v4.xdr.uint64_t;
import org.dcache.chimera.nfs.v4.xdr.createmode4;
import org.dcache.chimera.nfs.v4.xdr.nfs_opnum4;
import org.dcache.chimera.nfs.v4.xdr.OPEN4resok;
import org.dcache.chimera.nfs.v4.xdr.OPEN4res;
import org.dcache.chimera.nfs.ChimeraNFSException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.dcache.chimera.ChimeraFsException;
import org.dcache.chimera.FileExistsChimeraFsException;
import org.dcache.chimera.FileNotFoundHimeraFsException;
import org.dcache.chimera.FsInode;
import org.dcache.chimera.posix.AclHandler;
import org.dcache.chimera.posix.Stat;
import org.dcache.chimera.posix.UnixAcl;

public class OperationOPEN extends AbstractNFSv4Operation {

    private static final Logger _log = Logger.getLogger(OperationOPEN.class.getName());

    OperationOPEN(nfs_argop4 args) {
        super(args, nfs_opnum4.OP_OPEN);
    }

    @Override
    public boolean process(CompoundContext context) {
        OPEN4res res = new OPEN4res();

        try {

            Long clientid = Long.valueOf(_args.opopen.owner.value.clientid.value.value);
            NFS4Client client = null;

            if (context.getSession() == null) {
                client = NFSv4StateHandler.getInstace().getClientByID(clientid);

                if (client == null || !client.isConfirmed()) {
                    throw new ChimeraNFSException(nfsstat4.NFS4ERR_STALE_CLIENTID, "bad client id.");
                }

                client.updateLeaseTime(NFSv4Defaults.NFS4_LEASE_TIME);
                _log.log(Level.FINEST, "open request form clientid: {0}, owner: {1}",
                        new Object[]{client, new String(_args.opopen.owner.value.owner)});
            } else {
                client = context.getSession().getClient();
            }

            res.resok4 = new OPEN4resok();
            res.resok4.attrset = new bitmap4();
            res.resok4.attrset.value = new uint32_t[2];
            res.resok4.attrset.value[0] = new uint32_t(0);
            res.resok4.attrset.value[1] = new uint32_t(0);
            res.resok4.delegation = new open_delegation4();
            res.resok4.delegation.delegation_type = open_delegation_type4.OPEN_DELEGATE_NONE;

            switch (_args.opopen.claim.claim) {

                case open_claim_type4.CLAIM_NULL:

                    if (!context.currentInode().isDirectory()) {
                        throw new ChimeraNFSException(nfsstat4.NFS4ERR_NOTDIR, "not a directory");
                    }

                    String name = NameFilter.convert(_args.opopen.claim.file.value.value.value);
                    _log.log(Level.FINEST, "regular open for : {0}", name);

                    FsInode inode;
                    if (_args.opopen.openhow.opentype == opentype4.OPEN4_CREATE) {

                        boolean exclusive = _args.opopen.openhow.how.mode == createmode4.EXCLUSIVE4;

                        try {

                            inode = context.currentInode().inodeOf(name);

                            if (exclusive) {
                                throw new ChimeraNFSException(nfsstat4.NFS4ERR_EXIST, "file already exist");
                            }

                            _log.log(Level.FINEST, "Opening existing file: {0}", name);

                            _log.finest("Check permission");
                            // check file permissions
                            Stat fileStat = inode.statCache();
                            _log.log(Level.FINEST, "UID  : {0}", fileStat.getUid());
                            _log.log(Level.FINEST, "GID  : {0}", fileStat.getGid());
                            _log.log(Level.FINEST, "Mode : 0{0}", Integer.toOctalString(fileStat.getMode() & 0777));
                            UnixAcl fileAcl = new UnixAcl(fileStat.getUid(), fileStat.getGid(), fileStat.getMode() & 0777);
                            if (!context.getAclHandler().isAllowed(fileAcl, context.getUser(), AclHandler.ACL_WRITE)) {
                                throw new ChimeraNFSException(nfsstat4.NFS4ERR_ACCESS, "Permission denied.");
                            }

                            OperationSETATTR.setAttributes(_args.opopen.openhow.how.createattrs, inode);
                        } catch (FileNotFoundHimeraFsException he) {

                            // check parent permissions
                            Stat parentStat = context.currentInode().statCache();
                            UnixAcl parentAcl = new UnixAcl(parentStat.getUid(), parentStat.getGid(), parentStat.getMode() & 0777);
                            if (!context.getAclHandler().isAllowed(parentAcl, context.getUser(), AclHandler.ACL_INSERT)) {
                                throw new ChimeraNFSException(nfsstat4.NFS4ERR_ACCESS, "Permission denied.");
                            }

                            _log.log(Level.FINEST, "Creating a new file: {0}", name);
                            inode = context.currentInode().create(name, context.getUser().getUID(),
                                    context.getUser().getGID(), 0600);

                            if (!exclusive) {
                                 res.resok4.attrset = OperationSETATTR.setAttributes(_args.opopen.openhow.how.createattrs, inode);
                            }
                        }

                    } else {

                        inode = context.currentInode().inodeOf(name);

                        Stat inodeStat = inode.statCache();
                        UnixAcl fileAcl = new UnixAcl(inodeStat.getUid(), inodeStat.getGid(), inodeStat.getMode() & 0777);
                        if (!context.getAclHandler().isAllowed(fileAcl, context.getUser(), AclHandler.ACL_READ)) {
                            throw new ChimeraNFSException(nfsstat4.NFS4ERR_ACCESS, "Permission denied.");
                        }

                        if (inode.isDirectory()) {
                            throw new ChimeraNFSException(nfsstat4.NFS4ERR_ISDIR, "path is a directory");
                        }

                        if (inode.isLink()) {
                            throw new ChimeraNFSException(nfsstat4.NFS4ERR_SYMLINK, "path is a symlink");
                        }
                    }

                    context.currentInode(inode);

                    break;
                case open_claim_type4.CLAIM_PREVIOUS:
                    _log.log(Level.FINEST, "open by Inode for : {0}", context.currentInode().toFullString());
                    break;
                case open_claim_type4.CLAIM_DELEGATE_CUR:
                    break;
                case open_claim_type4.CLAIM_DELEGATE_PREV:
                    break;
            }

            res.resok4.cinfo = new change_info4();
            res.resok4.cinfo.atomic = true;
            res.resok4.cinfo.before = new changeid4(new uint64_t(context.currentInode().statCache().getMTime()));
            res.resok4.cinfo.after = new changeid4(new uint64_t(System.currentTimeMillis()));

            NFS4State nfs4state = null;
            /*
             * if it's not session-based  request, then client have to confirm
             */
            if (context.getSession() == null) {
                res.resok4.rflags = new uint32_t(nfs4_prot.OPEN4_RESULT_LOCKTYPE_POSIX
                        | nfs4_prot.OPEN4_RESULT_CONFIRM);
                nfs4state = new NFS4State(_args.opopen.seqid.value.value);
            } else {
                res.resok4.rflags = new uint32_t(nfs4_prot.OPEN4_RESULT_LOCKTYPE_POSIX);
                nfs4state = new NFS4State(context.getSession().getClient().currentSeqID());
                context.getSession().getClient().nextSeqID();
            }

            res.resok4.stateid = nfs4state.stateid();
            client.addState(nfs4state);
            NFSv4StateHandler.getInstace().addClinetByStateID(nfs4state.stateid(), clientid);
            _log.log(Level.FINEST, "New stateID: {0}", nfs4state.stateid());

            res.status = nfsstat4.NFS4_OK;

        } catch (ChimeraNFSException he) {
            _log.log(Level.FINE, "OPEN: ", he.getMessage());
            res.status = he.getStatus();
        } catch (FileExistsChimeraFsException e) {
            _log.log(Level.FINE, "OPEN: " + e.getMessage());
            res.status = nfsstat4.NFS4ERR_EXIST;
        } catch (FileNotFoundHimeraFsException fnf) {
            _log.log(Level.FINE, "OPEN: " + fnf.getMessage());
            res.status = nfsstat4.NFS4ERR_NOENT;
        } catch (ChimeraFsException hfe) {
            _log.log(Level.WARNING, "OPEN:", hfe);
            res.status = nfsstat4.NFS4ERR_SERVERFAULT;
        } catch (Exception e) {
            _log.log(Level.SEVERE, "OPEN:", e);
            res.status = nfsstat4.NFS4ERR_SERVERFAULT;
        }

        _result.opopen = res;

        context.processedOperations().add(_result);
        return res.status == nfsstat4.NFS4_OK;

    }
}