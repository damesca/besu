
package org.hyperledger.besu.psi;

import edu.alibaba.mpc4j.s2pc.pso.psi.PsiUtils;

public class PsiTest {
    
    public PsiTest() {
        int length = PsiUtils.getMaliciousPeqtByteLength(100, 100);
        /*LOG*/System.out.println(length);
    }

    public String hello() {
        return "Psi hello";
    }

}
