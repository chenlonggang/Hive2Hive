package org.hive2hive.core.processes.implementations.login;

import java.io.IOException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

import javax.crypto.SecretKey;

import net.tomp2p.futures.FuturePut;

import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.hive2hive.core.H2HConstants;
import org.hive2hive.core.H2HJUnitTest;
import org.hive2hive.core.exceptions.GetFailedException;
import org.hive2hive.core.exceptions.NoPeerConnectionException;
import org.hive2hive.core.model.UserProfile;
import org.hive2hive.core.network.NetworkManager;
import org.hive2hive.core.network.NetworkTestUtil;
import org.hive2hive.core.network.data.parameters.Parameters;
import org.hive2hive.core.processes.util.UseCaseTestUtil;
import org.hive2hive.core.security.EncryptedNetworkContent;
import org.hive2hive.core.security.H2HEncryptionUtil;
import org.hive2hive.core.security.PasswordUtil;
import org.hive2hive.core.security.UserCredentials;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests the generic step that puts the user profile into the DHT
 * 
 * @author Nico, Seppi
 */
public class GetUserProfileStepTest extends H2HJUnitTest {

	private static List<NetworkManager> network;
	private static final int networkSize = 2;

	@BeforeClass
	public static void initTest() throws Exception {
		testClass = GetUserProfileStepTest.class;
		beforeClass();
		network = NetworkTestUtil.createNetwork(networkSize);
	}

	@Test
	public void testStepSuccess() throws InterruptedException, InvalidKeySpecException, DataLengthException,
			IllegalStateException, InvalidCipherTextException, ClassNotFoundException, IOException,
			NoPeerConnectionException, GetFailedException {
		NetworkManager putter = network.get(0); // where the process runs

		// create the needed objects
		UserCredentials credentials = NetworkTestUtil.generateRandomCredentials();

		UserProfile testProfile = new UserProfile(credentials.getUserId());

		// add them already to the DHT
		SecretKey encryptionKeys = PasswordUtil.generateAESKeyFromPassword(credentials.getPassword(), credentials.getPin(),
				H2HConstants.KEYLENGTH_USER_PROFILE);
		EncryptedNetworkContent encrypted = H2HEncryptionUtil.encryptAES(testProfile, encryptionKeys);
		FuturePut putGlobal = putter.getDataManager().putUnblocked(
				new Parameters().setLocationKey(credentials.getProfileLocationKey())
						.setContentKey(H2HConstants.USER_PROFILE).setData(encrypted));
		putGlobal.awaitUninterruptibly();

		UserProfile profile = UseCaseTestUtil.getUserProfile(putter, credentials);

		// verify if both objects are the same
		Assert.assertEquals(credentials.getUserId(), profile.getUserId());
	}

	@Test
	public void testStepSuccessWithNoUserProfile() throws NoPeerConnectionException {
		// create the needed objects
		UserCredentials credentials = NetworkTestUtil.generateRandomCredentials();
		try {
			UseCaseTestUtil.getUserProfile(network.get(0), credentials);
			Assert.fail("Should have triggered a GetFailedException");
		} catch (GetFailedException e) {
			// has to be triggered here
		}
	}

	@AfterClass
	public static void endTest() {
		NetworkTestUtil.shutdownNetwork(network);
		afterClass();
	}
}
