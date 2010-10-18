package org.dcache.chimera.nfs.v4.client;

import org.dcache.chimera.nfs.v4.xdr.EXCHANGE_ID4args;
import org.dcache.chimera.nfs.v4.xdr.client_owner4;
import org.dcache.chimera.nfs.v4.xdr.int64_t;
import org.dcache.chimera.nfs.v4.xdr.nfs4_prot;
import org.dcache.chimera.nfs.v4.xdr.nfs_argop4;
import org.dcache.chimera.nfs.v4.xdr.nfs_impl_id4;
import org.dcache.chimera.nfs.v4.xdr.nfs_opnum4;
import org.dcache.chimera.nfs.v4.xdr.nfstime4;
import org.dcache.chimera.nfs.v4.xdr.state_protect4_a;
import org.dcache.chimera.nfs.v4.xdr.uint32_t;
import org.dcache.chimera.nfs.v4.xdr.verifier4;

import static org.dcache.chimera.nfs.v4.HimeraNFS4Utils.string2utf8str_cis;
import static org.dcache.chimera.nfs.v4.HimeraNFS4Utils.string2utf8str_cs;

public class ExchangeIDStub {


    public static nfs_argop4 normal(String nii_domain, String nii_name,
            String co_ownerid, int flags, int how) {

        nfs_argop4 op = new nfs_argop4();
        op.argop = nfs_opnum4.OP_EXCHANGE_ID;
        op.opexchange_id = new EXCHANGE_ID4args();
        op.opexchange_id.eia_client_impl_id = new nfs_impl_id4[1];
        nfs_impl_id4 n4 = new nfs_impl_id4();
        n4.nii_domain = string2utf8str_cis(nii_domain);
        n4.nii_name = string2utf8str_cs(nii_name);
        op.opexchange_id.eia_client_impl_id[0] = n4;

        nfstime4 releaseDate = new nfstime4();
        releaseDate.nseconds = new uint32_t(0);
        releaseDate.seconds = new int64_t(System.currentTimeMillis() / 1000);

        op.opexchange_id.eia_client_impl_id[0].nii_date = releaseDate;
        op.opexchange_id.eia_clientowner = new client_owner4();
        op.opexchange_id.eia_clientowner.co_ownerid = co_ownerid.getBytes();

        op.opexchange_id.eia_clientowner.co_verifier = new verifier4();
        op.opexchange_id.eia_clientowner.co_verifier.value = new byte[nfs4_prot.NFS4_VERIFIER_SIZE];

        byte[] locVerifier = Long.toHexString(releaseDate.seconds.value).getBytes();


        int len = locVerifier.length > nfs4_prot.NFS4_VERIFIER_SIZE ? nfs4_prot.NFS4_VERIFIER_SIZE : locVerifier.length;
        System.arraycopy(locVerifier, 0, op.opexchange_id.eia_clientowner.co_verifier.value, 0,len );

        op.opexchange_id.eia_flags = new uint32_t(flags);
        op.opexchange_id.eia_state_protect = new state_protect4_a();
        op.opexchange_id.eia_state_protect.spa_how = how;
        return op;
    }

}