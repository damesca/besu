
package org.hyperledger.besu.psi;

import edu.alibaba.mpc4j.s2pc.pso.psi.PsiClient;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiConfig;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiFactory;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiServer;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiUtils;
import edu.alibaba.mpc4j.s2pc.pso.psi.hfh99.Hfh99ByteEccPsiConfig;
import edu.alibaba.mpc4j.s2pc.pso.psi.kkrt16.Kkrt16PsiConfig;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.RpcManager;
import edu.alibaba.mpc4j.common.rpc.impl.http.HttpRpcManager;
import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.common.rpc.impl.http.HttpParty;
import edu.alibaba.mpc4j.common.rpc.impl.http.HttpRpc;

public class PsiTest {

    // TODO: change these to final and init with a builder
    private final RpcManager rpcManager;
    private final Rpc serverRpc;
    private final Rpc clientRpc;
    private final PsiConfig config;
    private final PsiClient<ByteBuffer> psiClient;
    private final PsiServer<ByteBuffer> psiServer;

    public PsiTest(final int parties, final List<String> addresses, final String URI) {
        /*LOG*/System.out.println("[PsiTest] creating HttpRpcManager...");
        this.rpcManager = new HttpRpcManager(parties, addresses, URI);
        /*LOG*/System.out.println("[PsiTest] getting serverRpc...");
        this.serverRpc = rpcManager.getRpc(0);
        /*LOG*/System.out.println("[PsiTest] getting clientRpc...");
        this.clientRpc = rpcManager.getRpc(1);
        /*LOG*/System.out.println("[PsiTest] partyAddresses");
        for(int i = 0; i < 2; i++) {
            HttpParty pt = (HttpParty) this.serverRpc.getParty(i);
            String address = pt.getPartyAddress();
            /*LOG*/System.out.println(address);
        }
        //this.config = new Kkrt16PsiConfig.Builder().build();
        this.config = new Hfh99ByteEccPsiConfig.Builder().setEnvType(EnvType.STANDARD_JDK).build();
        this.psiClient = PsiFactory.createClient(clientRpc, serverRpc.ownParty(), config);
        this.psiServer = PsiFactory.createServer(serverRpc, clientRpc.ownParty(), config);
    }

    public Set<ByteBuffer> runPsiClient(final Set<ByteBuffer> clientElementSet, final int serverElementSize) {
        psiClient.getRpc().connect();
        try{
            psiClient.init(clientElementSet.size(), serverElementSize);
            Set<ByteBuffer> intersectionSet = psiClient.psi(clientElementSet, serverElementSize);
            psiClient.getRpc().disconnect();
            return intersectionSet;
        }catch(MpcAbortException e){
            /*LOG*/System.out.println("MpcAbortException");
            return null;
        }
    }

    public Set<ByteBuffer> runPsiServer(final Set<ByteBuffer> serverElementSet, final int clientElementSize) {
        psiServer.getRpc().connect();
        try{
            psiServer.init(serverElementSet.size(), clientElementSize);
            Set<ByteBuffer> intersectionSet = psiClient.psi(serverElementSet, clientElementSize);
            psiServer.getRpc().disconnect();
            return intersectionSet;
        }catch(MpcAbortException e){
            /*LOG*/System.out.println("MpcAbortException");
            return null;
        }
    }

}
