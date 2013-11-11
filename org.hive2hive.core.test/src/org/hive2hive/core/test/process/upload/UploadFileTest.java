package org.hive2hive.core.test.process.upload;

import java.io.File;
import java.io.IOException;
import java.security.KeyPair;
import java.util.List;
import java.util.Random;

import org.apache.commons.io.FileUtils;
import org.hive2hive.core.IH2HFileConfiguration;
import org.hive2hive.core.file.FileManager;
import org.hive2hive.core.model.FileTreeNode;
import org.hive2hive.core.model.MetaFile;
import org.hive2hive.core.model.UserProfile;
import org.hive2hive.core.network.NetworkManager;
import org.hive2hive.core.process.register.RegisterProcess;
import org.hive2hive.core.process.upload.UploadFileProcess;
import org.hive2hive.core.security.UserCredentials;
import org.hive2hive.core.test.H2HJUnitTest;
import org.hive2hive.core.test.H2HWaiter;
import org.hive2hive.core.test.integration.TestH2HFileConfiguration;
import org.hive2hive.core.test.network.NetworkGetUtil;
import org.hive2hive.core.test.network.NetworkTestUtil;
import org.hive2hive.core.test.process.TestProcessListener;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests uploading a file.
 * 
 * @author Nico
 * 
 */
public class UploadFileTest extends H2HJUnitTest {

	private final int networkSize = 10;
	private List<NetworkManager> network;
	private UserCredentials userCredentials;
	private FileManager fileManager;
	private IH2HFileConfiguration config = new TestH2HFileConfiguration();

	@BeforeClass
	public static void initTest() throws Exception {
		testClass = UploadFileTest.class;
		beforeClass();

	}

	@Before
	public void createProfile() {
		network = NetworkTestUtil.createNetwork(networkSize);
		userCredentials = NetworkTestUtil.generateRandomCredentials();

		// register a user
		RegisterProcess process = new RegisterProcess(userCredentials, network.get(0));
		TestProcessListener listener = new TestProcessListener();
		process.addListener(listener);
		process.start();

		H2HWaiter waiter = new H2HWaiter(20);
		do {
			waiter.tickASecond();
		} while (!listener.hasSucceeded());

		String randomName = NetworkTestUtil.randomString();
		File root = new File(System.getProperty("java.io.tmpdir"), randomName);
		fileManager = new FileManager(root);
	}

	@Test
	public void testUploadSingleChunk() throws IOException {
		File file = createFileRandomContent(1);

		startUploadProcess(file);
		verifyUpload(file, 1);
	}

	@Test
	public void testUploadMultipleChunks() throws IOException {
		// creates a file with length of at least 5 chunks
		File file = createFileRandomContent(5);

		startUploadProcess(file);
		verifyUpload(file, 5);
	}

	private File createFileRandomContent(int numOfChunks) throws IOException {
		// create file of size of multiple numbers of chunks
		String random = "";
		while (Math.ceil(1.0 * random.getBytes().length / config.getChunkSize()) < numOfChunks) {
			random += generateRandomString(config.getChunkSize() - 1);
		}

		String fileName = NetworkTestUtil.randomString();
		File file = new File(fileManager.getRoot(), fileName);
		FileUtils.write(file, random);

		return file;
	}

	private void startUploadProcess(File toUpload) {
		NetworkManager client = network.get(new Random().nextInt(networkSize));
		UploadFileProcess process = new UploadFileProcess(toUpload, userCredentials, client, fileManager,
				config);
		TestProcessListener listener = new TestProcessListener();
		process.addListener(listener);
		process.start();

		// wait maximally 1m because files could be large
		H2HWaiter waiter = new H2HWaiter(60);
		do {
			waiter.tickASecond();
		} while (!listener.hasSucceeded());

	}

	private void verifyUpload(File originalFile, int expectedChunks) throws IOException {
		// pick new client to test
		NetworkManager client = network.get(new Random().nextInt(networkSize));

		// test if there is something in the user profile
		UserProfile gotProfile = NetworkGetUtil.getUserProfile(client, userCredentials);
		Assert.assertNotNull(gotProfile);

		FileTreeNode node = gotProfile.getRoot().getChildByName(originalFile.getName());
		Assert.assertNotNull(node);

		// get the meta file with the keys (decrypt it)
		KeyPair metaFileKeys = node.getKeyPair();
		MetaFile metaFile = (MetaFile) NetworkGetUtil.getMetaDocument(client, metaFileKeys);
		Assert.assertNotNull(metaFile);
		Assert.assertEquals(1, metaFile.getVersions().size());
		Assert.assertEquals(expectedChunks, metaFile.getVersions().get(0).getChunkIds().size());

		// create new filemanager
		File root = new File(System.getProperty("java.io.tmpdir"), NetworkTestUtil.randomString());
		FileManager fileManager2 = new FileManager(root);
		File file = NetworkGetUtil.downloadFile(client, node, metaFile, fileManager2);
		Assert.assertTrue(file.exists());
		Assert.assertEquals(FileUtils.readFileToString(originalFile), FileUtils.readFileToString(file));
	}

	@Test
	public void testUploadWrongCredentials() throws IOException {
		userCredentials = NetworkTestUtil.generateRandomCredentials();
		File file = createFileRandomContent(1);

		NetworkManager client = network.get(new Random().nextInt(networkSize));
		UploadFileProcess process = new UploadFileProcess(file, userCredentials, client, fileManager, config);
		TestProcessListener listener = new TestProcessListener();
		process.addListener(listener);
		process.start();

		// wait maximally 1m because files could be large
		H2HWaiter waiter = new H2HWaiter(20);
		do {
			waiter.tickASecond();
		} while (!listener.hasFailed());
	}

	@After
	public void deleteAndShutdown() throws IOException {
		NetworkTestUtil.shutdownNetwork(network);
		FileUtils.deleteDirectory(fileManager.getRoot());
	}

	@AfterClass
	public static void endTest() throws IOException {
		afterClass();
	}
}