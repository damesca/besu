package org.hyperledger.besu.ethereum.privacy.storage;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public interface ExtendedPrivacyStorage {
    
    Optional<Bytes> getPrivateArgs(Bytes pmtHash);

    Updater updater();

    interface Updater {

        Updater putPrivateArgs(
            Bytes pmtHash, Bytes privateArgs);

        void commit();

        void rollback();

        void remove(final Bytes key);

    }

}
