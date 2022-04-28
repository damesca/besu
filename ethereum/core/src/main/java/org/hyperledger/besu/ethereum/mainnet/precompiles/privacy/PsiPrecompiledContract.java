package org.hyperledger.besu.ethereum.mainnet.precompiles.privacy;

import org.hyperledger.besu.evm.precompile.AbstractPrecompiledContract;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateStateGenesisAllocator;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.evm.Gas;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.evm.frame.MessageFrame;
import static org.hyperledger.besu.ethereum.mainnet.PrivateStateUtils.KEY_TRANSACTION_HASH;


public class PsiPrecompiledContract extends AbstractPrecompiledContract{
	private final Enclave enclave;
	final WorldStateArchive privateWorldStateArchive;
	final PrivateStateRootResolver privateStateRootResolver;
	private final PrivateStateGenesisAllocator privateStateGenesisAllocator;
	PrivateTransactionProcessor privateTransactionProcessor;

	private static final Logger LOG = LoggerFactory.getLogger(PsiPrecompiledContract.class);

	public PsiPrecompiledContract(
			final GasCalculator gasCalculator,
			final PrivacyParameters privacyParameters,
			final String name) {
		this(
				gasCalculator,
				privacyParameters.getEnclave(),
				privacyParameters.getPrivateWorldStateArchive(),
				privacyParameters.getPrivateStateRootResolver(),
				privacyParameters.getPrivateStateGenesisAllocator(),
				name);
	}

	protected PsiPrecompiledContract(
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
		LOG.info("PsiPrecompiledContract created");
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
		int code = enclave.hashCode();
		int code2 = privateStateGenesisAllocator.hashCode();
		return Bytes.ofUnsignedShort(code + code2);
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
}
