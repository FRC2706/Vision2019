package ca.team2706.vision.trackerboxreloaded;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

import javax.imageio.ImageIO;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;

public class Main {

	public static NetworkTable visionTable;

	/**
	 * A class to hold calibration parameters for the image processing algorithm
	 */
	public static class VisionParams {
		int minHue;
		int maxHue;
		int minSaturation;
		int maxSaturation;
		int minValue;
		int maxValue;
		int erodeDilateIterations;
		int cameraSelect;
		double aspectRatioThresh;
		double minArea;
		double distToCentreImportance;
		String imageFile;
	}

	public static VisionParams visionParams = new VisionParams();

	/**
	 * A class to hold any visionTable data returned by process() :) :) :} :] :]
	 */

	public static class VisionData {

		public static class Target {
			int xCentre;
			double xCentreNorm;
			int yCentre;
			double yCentreNorm;
			double areaNorm; // [0,1] representing how much of the screen it
								// occupies
			Rect boundingBox;
		}

		ArrayList<Target> targetsFound = new ArrayList<Target>();
		Target preferredTarget;
		public Mat outputImg = new Mat();
		public double fps;
	}

	/**
	 * Initilizes the Network Tables WARNING! Change 127.0.0.1 to the robot ip
	 * before it is on master or it will not be fun :)
	 */
	private static void initNetworkTables() {
		NetworkTableInstance instance = NetworkTableInstance.getDefault();
		instance.setUpdateRate(0.02);
		instance.startClientTeam(2706); // Comment this for non robot use
		// instance.startClient("127.0.0.1"); //Comment this for use on a robot
		// / plyboy
		visionTable = instance.getTable("vision");
	}

	/**
	 * Loads the visionTable params! :]
	 **/

	private static void loadVisionParams() {
		Properties properties = new Properties();
		try {
			FileInputStream in = new FileInputStream("visionParams.properties");
			properties.load(in);

			visionParams.cameraSelect = Integer.valueOf(properties.getProperty("CameraSelect"));
			visionParams.minHue = Integer.valueOf(properties.getProperty("minHue"));
			visionParams.maxHue = Integer.valueOf(properties.getProperty("maxHue"));
			visionParams.minSaturation = Integer.valueOf(properties.getProperty("minSaturation"));
			visionParams.maxSaturation = Integer.valueOf(properties.getProperty("maxSaturation"));
			visionParams.minValue = Integer.valueOf(properties.getProperty("minValue"));
			visionParams.maxValue = Integer.valueOf(properties.getProperty("maxValue"));
			visionParams.minArea = Double.valueOf(properties.getProperty("minArea"));
			visionParams.erodeDilateIterations = Integer.valueOf(properties.getProperty("erodeDilateIterations"));
			visionParams.aspectRatioThresh = Double.valueOf(properties.getProperty("aspectRatioThresh"));
			visionParams.distToCentreImportance = Double.valueOf(properties.getProperty("distToCentreImportance"));
			visionParams.imageFile = properties.getProperty("imageFile");
		} catch (Exception e1) {
			e1.printStackTrace();
			System.err.println("\n\nError reading the params file, check if the file is corrupt?");
			System.exit(1);
		}
	}

	public static void saveVisionParams() {
		Properties properties = new Properties();
		try {
			properties.setProperty("CameraSelect", String.valueOf(visionParams.cameraSelect));
			properties.setProperty("minHue", String.valueOf(visionParams.minHue));
			properties.setProperty("maxHue", String.valueOf(visionParams.maxHue));
			properties.setProperty("minSaturation", String.valueOf(visionParams.minSaturation));
			properties.setProperty("maxSaturation", String.valueOf(visionParams.maxSaturation));
			properties.setProperty("minValue", String.valueOf(visionParams.minValue));
			properties.setProperty("maxValue", String.valueOf(visionParams.maxValue));
			properties.setProperty("erodeDilateIterations", String.valueOf(visionParams.erodeDilateIterations));
			properties.setProperty("minArea", String.valueOf(visionParams.minArea));
			properties.setProperty("aspectRatioThresh", String.valueOf(visionParams.aspectRatioThresh));
			properties.setProperty("distToCentreImportance", String.valueOf(visionParams.distToCentreImportance));
			properties.setProperty("imageFile", visionParams.imageFile);

			FileOutputStream out = new FileOutputStream("visionParams.properties");
			properties.store(out, "");
		} catch (Exception e1) {
			e1.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Turns all the vision data into packets that kno da wae to get to the
	 * roborio :]
	 *
	 * @param visionData
	 */
	private static void sendVisionDataOverNetworkTables(VisionData visionData) {

		// Sends the data
		visionTable.getEntry("fps").setDouble(visionData.fps);
		visionTable.getEntry("numTargetsFound").setNumber(visionData.targetsFound.size());

		if (visionData.preferredTarget != null)
			visionTable.getEntry("ctrX").setDouble(visionData.preferredTarget.xCentreNorm);
	}

	/**
	 * Converts a OpenCV Matrix to a BufferedImage :)
	 *
	 * @param matrix
	 *            Matrix to be converted
	 * @return Generated from the matrix
	 * @throws IOException
	 * @throws Exception
	 */
	private static BufferedImage matToBufferedImage(Mat matrix) throws IOException {
		MatOfByte mob = new MatOfByte();
		Imgcodecs.imencode(".jpg", matrix, mob);
		byte ba[] = mob.toArray();

		BufferedImage bi = ImageIO.read(new ByteArrayInputStream(ba));
		return bi;
	}

	/**
	 * The main method! Very important Do not delete! :] :]
	 *
	 * @param args
	 */
	public static void main(String[] args) {
		// Must be included!
		System.loadLibrary("opencv_java310");

		// Connect NetworkTables, and get access to the publishing table
		initNetworkTables();
		// read the visionTable calibration values from file.
		loadVisionParams();
		Mat frame = new Mat();
		// Open a connection to the camera
		VideoCapture camera = null;

		// Whether to use a camera, or load an image file from disk.
		boolean useCamera = true;
		if (visionParams.cameraSelect == -1) {
			useCamera = false;
		}

		if (useCamera) {
			camera = new VideoCapture(visionParams.cameraSelect);
			camera.read(frame);

			if (!camera.isOpened()) {
				System.err.println("Error: Can not connect to camera");
				System.exit(1);
			}

			// Set up the camera feed
			camera.read(frame);
		} else {
			// load the image from file.
			try {
				frame = bufferedImageToMat(ImageIO.read(new File(visionParams.imageFile)));
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}

		DisplayGui guiRawImg = null;
		DisplayGui guiProcessedImg = null;
		boolean use_GUI = false;
		if (System.getProperty("os.name").toLowerCase().indexOf("windows") != -1) {
			use_GUI = true;
		}
		// Set up the GUI display windows
		if (use_GUI) {
			try {
				guiRawImg = new DisplayGui(matToBufferedImage(frame), "Raw Camera Image");
				guiProcessedImg = new DisplayGui(matToBufferedImage(frame), "Processed Image");
				new ParamsSelector();
			} catch (IOException e) {
				// means mat2BufferedImage broke
				// non-fatal error, let the program continue
			}
		}
		// Main video processing loop

		while (true) {
			if (useCamera) {
				if (!camera.read(frame)) {
					System.err.println("Error: Failed to get a frame from the camera");
					continue;
				}
			} // else use the image from disk that we loaded above

			// Process the frame!
			long pipelineStart = System.nanoTime();
			VisionData visionData = Pipeline.process(frame, visionParams);
			long pipelineEnd = System.nanoTime();

			Pipeline.selectPreferredTarget(visionData, visionParams);

			Mat rawOutputImg;
			if (use_GUI) {
				rawOutputImg = frame.clone();
				Pipeline.drawPreferredTarget(rawOutputImg, visionData);
			} else {
				rawOutputImg = frame;
			}

			sendVisionDataOverNetworkTables(visionData);

			// display the processed frame in the GUI
			if (use_GUI) {
				try {
					// May throw a NullPointerException if initializing
					// the window failed
					guiRawImg.updateImage(matToBufferedImage(rawOutputImg));
					guiProcessedImg.updateImage(matToBufferedImage(visionData.outputImg));
				} catch (IOException e) {
					// means mat2BufferedImage broke
					// non-fatal error, let the program continue
					continue;
				} catch (NullPointerException e) {
					e.printStackTrace();
					System.out.println("Window closed");
					Runtime.getRuntime().halt(0);
				} catch (Exception e) {
					// just in case
					e.printStackTrace();
					continue;
				}

				// Display the frame rate ono the console
				double pipelineTime = (((double) (pipelineEnd - pipelineStart)) / Pipeline.NANOSECONDS_PER_SECOND)
						* 1000;
				System.out.printf("Vision FPS: %3.2f, pipeline took: %3.2f ms\n", visionData.fps, pipelineTime, "");
			}
//			} else {
//				VisionData data = Pipeline.process(frame, visionParams);
//				Pipeline.selectPreferredTarget(data, visionParams);
//				Pipeline.drawPreferredTarget(frame, data);
//				sendVisionDataOverNetworkTables(data);
//				try {
//					guiRawImg.updateImage(matToBufferedImage(frame));
//					guiProcessedImg.updateImage(matToBufferedImage(data.outputImg));
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
//			}
		} // end main video processing loop
	}

	public static Mat bufferedImageToMat(BufferedImage bi) {
		Mat mat = new Mat(bi.getHeight(), bi.getWidth(), CvType.CV_8UC3);
		byte[] data = ((DataBufferByte) bi.getRaster().getDataBuffer()).getData();
		mat.put(0, 0, data);
		return mat;
	}
}