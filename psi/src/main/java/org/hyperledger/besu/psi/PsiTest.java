
package org.hyperledger.besu.psi;

import edu.alibaba.mpc4j.s2pc.pso.psi.PsiUtils;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.RpcManager;
import edu.alibaba.mpc4j.common.rpc.impl.netty.NettyRpcManager;

public class PsiTest {

    // TODO: change these to final and init with a builder
    RpcManager rpcManager;
    Rpc serverRpc;
    Rpc clientRpc;
    
    public PsiTest() {
        int length = PsiUtils.getMaliciousPeqtByteLength(100, 100);
        /*LOG*/System.out.println(length);
    }

    public String hello() {
        return "Psi hello";
    }

    // TODO: public class RpcBuilder
    //      .getServerRpc
    //      .getClientRpc
    //      .getConfig

    public void setRpc(final int parties, final int portNumber) {
        rpcManager = new NettyRpcManager(parties, portNumber);
        serverRpc = rpcManager.getRpc(0);
        clientRpc = rpcManager.getRpc(1);
    }

    public String getServerRpc() {
        return serverRpc.toString();
    }

}
