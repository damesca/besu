package org.hyperledger.besu.ethereum.privacy.storage;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public interface ExtendedPrivacyStorage {
    
    Optional<Bytes> getPrivateArgs(Bytes key);

    Optional<Bytes> getKeyByContractAddr(Bytes contractAddr);

    Updater updater();

    interface Updater {

        Updater putPrivateArgs(
            Bytes key, Bytes privateArgs);

        Updater putKeyByContractAddr(
            Bytes contractAddr, Bytes key);

        void commit();

        void rollback();

        void remove(final Bytes key);

    }

}
