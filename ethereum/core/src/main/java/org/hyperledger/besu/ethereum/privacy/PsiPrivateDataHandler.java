package org.hyperledger.besu.ethereum.privacy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

// TODO: it should distinguish between contract creation or method call
public class PsiPrivateDataHandler {

    //private final String zeroVal = "0x0000000000000000000000000000000000000000000000000000000000000000";
    
    public static Bytes getPrivateArgs(final PrivateTransaction tx) {

        Optional<Bytes> privateArgs = tx.getPrivateArgs();
        if(privateArgs.isPresent()){

            Bytes privateArgsValues;
            if(tx.isContractCreation()) {
                privateArgsValues = getArgsLoad(privateArgs.get());
            } else {
                privateArgsValues = getArgsConsume(privateArgs.get());
            }
            return privateArgsValues;

        }else{
            throw new RuntimeException("privateArgs is not present");
        }

    }

    private static Bytes getArgsLoad(final Bytes args) {
        /*LOG*/System.out.println(">> getArgsLoad()");
        String strArgs = args.toHexString().substring(2);
        List<String> argArray = new ArrayList<String>(strArgs.length() / 64);
        int i = 0;
        while (i < strArgs.length()) {
            argArray.add(strArgs.substring(i, i + 64));
            i += 64;
        }
        
        // Save the privateArgs
        String privateArgs = "0x";
        int numberItems = Integer.parseInt(argArray.get(4));
        for(int j = 5; j < 5 + numberItems; j++) {
            privateArgs = privateArgs.concat(argArray.get(j));
        }
        return Bytes.fromHexString(privateArgs);

        // senderAddr
        // receiverAddr
        // maxSizeUniverse
        // lengthEncoding
        // numberItems
        // item1
        // item2
        // ...
    }

    private static Bytes getArgsConsume(final Bytes args) {
        /*LOG*/System.out.println(">> getArgsConsume()");
        String strArgs = args.toHexString().substring(2);
        List<String> argArray = new ArrayList<String>(strArgs.length() / 64);
        int i = 0;
        while (i < strArgs.length()) {
            argArray.add(strArgs.substring(i, i + 64));
            i += 64;
        }

        // Save the privateArgs
        String privateArgs = "0x";
        int numberItems = Integer.parseInt(argArray.get(1));
        for(int j = 2; j < 2 + numberItems; j++) {
            privateArgs = privateArgs.concat(argArray.get(j));
        }
        return Bytes.fromHexString(privateArgs);

        // lengthEncoding
        // numItems
        // item1
        // item2
        // ...
    }

}
