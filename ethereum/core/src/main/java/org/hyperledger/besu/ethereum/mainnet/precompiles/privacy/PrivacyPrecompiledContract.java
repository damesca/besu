/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.mainnet.precompiles.privacy;

import static org.hyperledger.besu.datatypes.Hash.fromPlugin;
import static org.hyperledger.besu.ethereum.mainnet.PrivateStateUtils.KEY_IS_PERSISTING_PRIVATE_STATE;
import static org.hyperledger.besu.ethereum.mainnet.PrivateStateUtils.KEY_PRIVATE_METADATA_UPDATER;
import static org.hyperledger.besu.ethereum.mainnet.PrivateStateUtils.KEY_TRANSACTION_HASH;
import static org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver.EMPTY_ROOT_HASH;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.EnclaveConfigurationException;
import org.hyperledger.besu.enclave.EnclaveIOException;
import org.hyperledger.besu.enclave.EnclaveServerException;
import org.hyperledger.besu.enclave.types.ExtendedPrivacyResponse;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.privacy.PrivateStateGenesisAllocator;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PsiPrivateDataHandler;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionReceipt;
import org.hyperledger.besu.ethereum.privacy.storage.ExtendedPrivacyStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateBlockMetadata;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateTransactionMetadata;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.AbstractPrecompiledContract;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldState;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.Hash;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrivacyPrecompiledContract extends AbstractPrecompiledContract {
  private final Enclave enclave;
  final WorldStateArchive privateWorldStateArchive;
  final PrivateStateRootResolver privateStateRootResolver;
  private final PrivateStateGenesisAllocator privateStateGenesisAllocator;
  PrivateTransactionProcessor privateTransactionProcessor;

  private final ExtendedPrivacyStorage extendedPrivacyStorage;

  private static final Logger LOG = LoggerFactory.getLogger(PrivacyPrecompiledContract.class);

  public PrivacyPrecompiledContract(
      final GasCalculator gasCalculator,
      final PrivacyParameters privacyParameters,
      final String name) {
    this(
        gasCalculator,
        privacyParameters.getEnclave(),
        privacyParameters.getPrivateWorldStateArchive(),
        privacyParameters.getPrivateStateRootResolver(),
        privacyParameters.getPrivateStateGenesisAllocator(),
        name,
        privacyParameters.getExtendedPrivacyStorage());
  }

  protected PrivacyPrecompiledContract(
    final GasCalculator gasCalculator,
    final Enclave enclave,
    final WorldStateArchive worldStateArchive,
    final PrivateStateRootResolver privateStateRootResolver,
    final PrivateStateGenesisAllocator privateStateGenesisAllocator,
    final String name) {
  super(name, gasCalculator);
  this.enclave = enclave;
  this.privateWorldStateArchive = worldStateArchive;
  this.privateStateRootResolver = privateStateRootResolver;
  this.privateStateGenesisAllocator = privateStateGenesisAllocator;
  this.extendedPrivacyStorage = null;
}

  protected PrivacyPrecompiledContract(
      final GasCalculator gasCalculator,
      final Enclave enclave,
      final WorldStateArchive worldStateArchive,
      final PrivateStateRootResolver privateStateRootResolver,
      final PrivateStateGenesisAllocator privateStateGenesisAllocator,
      final String name,
      final ExtendedPrivacyStorage extendedPrivacyStorage) {
    super(name, gasCalculator);
    this.enclave = enclave;
    this.privateWorldStateArchive = worldStateArchive;
    this.privateStateRootResolver = privateStateRootResolver;
    this.privateStateGenesisAllocator = privateStateGenesisAllocator;
    this.extendedPrivacyStorage = extendedPrivacyStorage;
  }

  public void setPrivateTransactionProcessor(
      final PrivateTransactionProcessor privateTransactionProcessor) {
    this.privateTransactionProcessor = privateTransactionProcessor;
  }

  @Override
  public Gas gasRequirement(final Bytes input) {
    return Gas.of(0L);
  }

  @Override
  public Bytes compute(final Bytes input, final MessageFrame messageFrame) {

    /*LOG*/System.out.println(" >>> [PrivacyPrecompiledContract] compute()");

    if (skipContractExecution(messageFrame)) {
      return Bytes.EMPTY;
    }

    final org.hyperledger.besu.plugin.data.Hash pmtHash =
        messageFrame.getContextVariable(KEY_TRANSACTION_HASH);

    final String key = input.toBase64String();
    final ReceiveResponse receiveResponse;
    try {
      receiveResponse = getReceiveResponse(key);
    } catch (final EnclaveClientException e) {
      LOG.debug("Can not fetch private transaction payload with key {}", key, e);
      return Bytes.EMPTY;
    }

    final BytesValueRLPInput bytesValueRLPInput =
        new BytesValueRLPInput(
            Bytes.wrap(Base64.getDecoder().decode(receiveResponse.getPayload())), false);
    final PrivateTransaction privateTransaction =
        PrivateTransaction.readFrom(bytesValueRLPInput.readAsRlp());
    
    /*LOG*/System.out.println(privateTransaction.toString());

    Bytes privateResult = null;
    // Handle extendedPrivacy
    if (privateTransaction.hasExtendedPrivacy()) {
      /*LOG*/System.out.println(" >>> [PrivacyPrecompiledContract] hasExtendedPrivacy()");

      // Get type of extendedPrivacy
      Bytes extendedPrivacy = privateTransaction.getExtendedPrivacy().get();
      Bytes privateArgs;
      if(extendedPrivacy.compareTo(Bytes.fromHexString("0x01")) == 0) {
        // PSI type
        /*LOG*/System.out.println(" >>> extendedPrivacy type: PSI");

        privateArgs = Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000002");

        // If it is not contract creation
        //if(privateTransaction.getPrivateFor().isPresent()) {
        if(privateTransaction.getTo().isPresent()) {

          Optional<Bytes> possiblePrivateArgs = 
            extendedPrivacyStorage.getPrivateArgs(Bytes.wrap(key.getBytes(Charset.forName("UTF-8"))));

          if(!possiblePrivateArgs.isPresent()) {
            Optional<Bytes> retrievedKey = 
              extendedPrivacyStorage.getKeyByContractAddr(
                privateTransaction.getTo().get());
            if(retrievedKey.isPresent()){
              /*LOG*/System.out.println(">>> [PrivacyPrecompiledContract] RETRIEVED: key");
              /*LOG*/System.out.println(retrievedKey.get().toBase64String());
              Optional<Bytes> privArgs = 
                extendedPrivacyStorage.getPrivateArgs(
                  retrievedKey.get());
              if(privArgs.isPresent()) {
                /*LOG*/System.out.println(">>> [PrivacyPrecompiledContract] RETRIEVED: privateArgs");
                /*LOG*/System.out.println(privArgs.get().toHexString());
                privateArgs = privArgs.get();
              } else {
                /*LOG*/System.out.println(">>> [PrivacyPrecompiledContract] privateArgs NOT PRESENT");
              }
            }
          } else {
            privateArgs = possiblePrivateArgs.get();
          }

          final ArrayList<String> privateFor = new ArrayList<>();
          privateFor.addAll(
            privateTransaction.getPrivateFor().get().stream()
                .map(Bytes::toBase64String)
                .collect(Collectors.toList()));
          String[] recipients = new String[privateFor.size() + 1];
          // recipients = [privateFrom, privateFor(0), privateFor(1), ...]
          recipients[0] = privateTransaction.getPrivateFrom().toBase64String();
          for(int i = 1; i < recipients.length; i++) {
            recipients[i] = privateFor.get(i-1);
          }
          /*LOG*/System.out.println(">>> [PrivacyPrecompiledContract] recipients");
          for(String item : recipients) {
            /*LOG*/System.out.println(item);
          }

          // This derives to a communication Tessera2Tessera, where the privacy protocol is executed.
          /*LOG*/System.out.println(">>> [PrivacyPrecompiledContract] extendedPrivacySend");
          /*LOG*/System.out.println(privateArgs.toHexString());
          ExtendedPrivacyResponse extResponse = enclave.extendedPrivacySend(
            extendedPrivacy.toArray(),
            privateArgs.toArray(),
            key.getBytes(Charset.defaultCharset()),
            recipients
          );
          /*LOG*/System.out.println(" >>> [PrivacyPrecompiledContract] extResponse");
          /*LOG*/System.out.println(Bytes.wrap(extResponse.getResult()).toHexString());
          privateResult = Bytes.wrap(extResponse.getResult());
        } else {
          privateResult = Bytes.fromHexString("0x00");
        }

      }else{
        /*LOG*/System.out.println(" >>> extendedPrivacy type: other");
        privateArgs = Bytes.fromHexString("0x00");
        privateResult = Bytes.fromHexString("0x00");
      }

    } else {
      privateResult = Bytes.fromHexString("0x00");
    }

    final Bytes privateFrom = privateTransaction.getPrivateFrom();
    if (!privateFromMatchesSenderKey(privateFrom, receiveResponse.getSenderKey())) {
      return Bytes.EMPTY;
    }

    final Bytes32 privacyGroupId =
        Bytes32.wrap(Bytes.fromBase64String(receiveResponse.getPrivacyGroupId()));

    try {
      if (privateTransaction.getPrivateFor().isEmpty()
          && !enclave
              .retrievePrivacyGroup(privacyGroupId.toBase64String())
              .getMembers()
              .contains(privateFrom.toBase64String())) {
        return Bytes.EMPTY;
      }
    } catch (final EnclaveClientException e) {
      // This exception is thrown when the privacy group can not be found
      return Bytes.EMPTY;
    } catch (final EnclaveServerException e) {
      throw new IllegalStateException(
          "Enclave is responding with an error, perhaps it has a misconfiguration?", e);
    } catch (final EnclaveIOException e) {
      throw new IllegalStateException("Can not communicate with enclave, is it up?", e);
    }

    LOG.debug("Processing private transaction {} in privacy group {}", pmtHash, privacyGroupId);

    final PrivateMetadataUpdater privateMetadataUpdater =
        messageFrame.getContextVariable(KEY_PRIVATE_METADATA_UPDATER);
    final Hash lastRootHash =
        privateStateRootResolver.resolveLastStateRoot(privacyGroupId, privateMetadataUpdater);

    final MutableWorldState disposablePrivateState =
        privateWorldStateArchive.getMutable(fromPlugin(lastRootHash), null).get();

    final WorldUpdater privateWorldStateUpdater = disposablePrivateState.updater();

    maybeApplyGenesisToPrivateWorldState(
        lastRootHash,
        disposablePrivateState,
        privateWorldStateUpdater,
        privacyGroupId,
        messageFrame.getBlockValues().getNumber());

    final TransactionProcessingResult result =
        processPrivateTransaction(
            messageFrame, privateTransaction, privacyGroupId, privateWorldStateUpdater);
    
    final TransactionProcessingResult modifiedResult;
    /*LOG*/System.out.println(">>> [PrivacyPrecompiledContract] privateResult");
    /*LOG*/System.out.println(privateResult.toHexString());
    if(privateResult.compareTo(Bytes.fromHexString("0x00")) != 0) {
      /*LOG*/System.out.println(" COMPARISON OK");
      modifiedResult = new TransactionProcessingResult(
        result.getStatus(),
        result.getLogs(),
        result.getEstimateGasUsedByTransaction(),
        result.getGasRemaining(),
        privateResult,
        result.getValidationResult(),
        result.getRevertReason()
      );
    } else {
      modifiedResult = result;
    }


    if (result.isInvalid() || !result.isSuccessful()) {
      LOG.error(
          "Failed to process private transaction {}: {}",
          pmtHash,
          result.getValidationResult().getErrorMessage());

      privateMetadataUpdater.putTransactionReceipt(pmtHash, new PrivateTransactionReceipt(/*result*/modifiedResult));

      return Bytes.EMPTY;
    }

    if (messageFrame.getContextVariable(KEY_IS_PERSISTING_PRIVATE_STATE, false)) {
      privateWorldStateUpdater.commit();
      disposablePrivateState.persist(null);

      storePrivateMetadata(
          pmtHash, privacyGroupId, disposablePrivateState, privateMetadataUpdater, /*result*/modifiedResult);
    }

    // If it is contractCreation and this is creator node, it must link privateArgs to contractAddr
    if(privateTransaction.isContractCreation()) {
      Optional<Bytes> possiblePrivateArgs = 
        extendedPrivacyStorage.getPrivateArgs(Bytes.wrap(key.getBytes(Charset.forName("UTF-8"))));
      if(possiblePrivateArgs.isPresent()) {
        Address contractAddr = Address.privateContractAddress(
          privateTransaction.getSender(), 
          privateTransaction.getNonce(), 
          privacyGroupId);
        /*LOG*/System.out.println(" >>> [PrivacyPrecompiledContract] PrivateContractAddress");
        /*LOG*/System.out.println(contractAddr.toHexString());

        ExtendedPrivacyStorage.Updater updater = extendedPrivacyStorage.updater();
        updater.putPrivateArgs(contractAddr, Bytes.wrap(key.getBytes(Charset.forName("UTF-8"))));
        updater.commit();
      }
    }

    /*LOG*/System.out.println(">>> [PrivacyPrecompiledContract] result.getOutput()");
    /*LOG*/System.out.println(modifiedResult.getOutput());

    return /*result*/modifiedResult.getOutput();
  }

  protected void maybeApplyGenesisToPrivateWorldState(
      final Hash lastRootHash,
      final MutableWorldState disposablePrivateState,
      final WorldUpdater privateWorldStateUpdater,
      final Bytes32 privacyGroupId,
      final long blockNumber) {
    if (lastRootHash.equals(EMPTY_ROOT_HASH)) {
      this.privateStateGenesisAllocator.applyGenesisToPrivateWorldState(
          disposablePrivateState, privateWorldStateUpdater, privacyGroupId, blockNumber);
    }
  }

  void storePrivateMetadata(
      final org.hyperledger.besu.plugin.data.Hash commitmentHash,
      final Bytes32 privacyGroupId,
      final MutableWorldState disposablePrivateState,
      final PrivateMetadataUpdater privateMetadataUpdater,
      final TransactionProcessingResult result) {

    final int txStatus =
        result.getStatus() == TransactionProcessingResult.Status.SUCCESSFUL ? 1 : 0;

    final PrivateTransactionReceipt privateTransactionReceipt =
        new PrivateTransactionReceipt(
            txStatus, result.getLogs(), result.getOutput(), result.getRevertReason());

    privateMetadataUpdater.putTransactionReceipt(commitmentHash, privateTransactionReceipt);
    privateMetadataUpdater.updatePrivacyGroupHeadBlockMap(privacyGroupId);
    privateMetadataUpdater.addPrivateTransactionMetadata(
        privacyGroupId,
        new PrivateTransactionMetadata(
            fromPlugin(commitmentHash), disposablePrivateState.rootHash()));
  }

  TransactionProcessingResult processPrivateTransaction(
      final MessageFrame messageFrame,
      final PrivateTransaction privateTransaction,
      final Bytes32 privacyGroupId,
      final WorldUpdater privateWorldStateUpdater) {

    return privateTransactionProcessor.processTransaction(
        messageFrame.getWorldUpdater(),
        privateWorldStateUpdater,
        (ProcessableBlockHeader) messageFrame.getBlockValues(),
        messageFrame.getContextVariable(KEY_TRANSACTION_HASH),
        privateTransaction,
        messageFrame.getMiningBeneficiary(),
        OperationTracer.NO_TRACING,
        messageFrame.getBlockHashLookup(),
        privacyGroupId);
  }

  ReceiveResponse getReceiveResponse(final String key) {
    final ReceiveResponse receiveResponse;
    try {
      receiveResponse = enclave.receive(key);
    } catch (final EnclaveServerException e) {
      throw new IllegalStateException(
          "Enclave is responding with an error, perhaps it has a misconfiguration?", e);
    } catch (final EnclaveIOException e) {
      throw new IllegalStateException("Can not communicate with enclave is it up?", e);
    }
    return receiveResponse;
  }

  boolean skipContractExecution(final MessageFrame messageFrame) {
    return isSimulatingPMT(messageFrame) || isMining(messageFrame);
  }

  boolean isSimulatingPMT(final MessageFrame messageFrame) {
    // If there's no PrivateMetadataUpdater, the precompile has not been called through the
    // PrivacyBlockProcessor. This indicates the PMT is being simulated and execution of the
    // precompile is not required.
    return !messageFrame.hasContextVariable(KEY_PRIVATE_METADATA_UPDATER);
  }

  boolean isMining(final MessageFrame messageFrame) {
    boolean isMining = false;
    final BlockValues currentBlockHeader = messageFrame.getBlockValues();
    if (!BlockHeader.class.isAssignableFrom(currentBlockHeader.getClass())) {
      if (messageFrame.getContextVariable(KEY_IS_PERSISTING_PRIVATE_STATE, false)) {
        throw new IllegalArgumentException(
            "The MessageFrame contains an illegal block header type. Cannot persist private block"
                + " metadata without current block hash.");
      } else {
        isMining = true;
      }
    }
    return isMining;
  }

  protected boolean privateFromMatchesSenderKey(
      final Bytes transactionPrivateFrom, final String payloadSenderKey) {
    if (payloadSenderKey == null) {
      LOG.warn(
          "Missing sender key from Orion response. Upgrade Orion to 1.6 to enforce privateFrom check.");
      throw new EnclaveConfigurationException(
          "Incompatible Orion version. Orion version must be 1.6.0 or greater.");
    }

    if (transactionPrivateFrom == null || transactionPrivateFrom.isEmpty()) {
      LOG.warn("Private transaction is missing privateFrom");
      return false;
    }

    if (!payloadSenderKey.equals(transactionPrivateFrom.toBase64String())) {
      LOG.warn("Private transaction privateFrom doesn't match payload sender key");
      return false;
    }

    return true;
  }
}
