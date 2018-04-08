import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;

import com.opencsv.CSVWriter;

/**
 * @author Jarrod Risley
 *
 */
public class DropletAnalyzer {

	/*
	 * Local Class Variables
	 */
	private Rect boundingRectangle;
	private ArrayList<double[]> compressionData = new ArrayList<double[]>(); // Data is stored as [a, b, a/b, height, relativeToNode]
	private ArrayList<double[]> relaxationData = new ArrayList<double[]>(); // Data is stored as [a, b, a/b, height, relativeToNode]
	private ArrayList<Mat> analyzedVideo = new ArrayList<Mat>();
	private ArrayList<Mat> analyzedCompressionVideo = new ArrayList<Mat>();
	private ArrayList<Mat> analyzedRelaxationVideo = new ArrayList<Mat>();
	private VideoCapture videoSource;
	private VideoWriter videoWriter;
	private Mat currentFrame;
	
	private final double calibrationConstant = 0.81915/129.67; // 0.351; // pixels/mircons
	private final double nodeLocation = 1718; // microns above the bottom of the field of view
	private final double heightAboveHorn = 1206; // microns above the horn to the bottom of the field of view
	private double baRatio;
	private double startLocation;
	private long startTime;
	private long endTime;
	private double x;
	private double y;
	private boolean startLocationMeasured = false;
	private boolean isCompressing;
	private boolean multiFlag;
	
	private String path;
	private String filename;
	private final String defaultPath = "D:\\Test Data\\"; //"D:\\Google Drive\\Lutchen Fellowship 2015\\Data\\Test Runs\\";
	private final int scale = 1;
	private final int delta = 0;
	private final int depth = CvType.CV_8UC1;
	private int runCounter = 0;
	private int maxNumberOfRuns = 0;
	
	/*
	 * Test Parameters
	 */
	private final boolean debugMode = false;
	
	/**
	 * Constructor
	 * @param target USB index for the camera
	 * 
	 */

	public DropletAnalyzer(int target) {
		
//		// Camera Enumerator. Use to debug camera issues.
//		int[] index = new int[10];
//		
//		for (int z = 0; z < 10; z++) {
//			
//			videoSource = new VideoCapture(z);
//			if (videoSource.isOpened()) {
//				index[z] = 1;
//			}
//			
//		}
//		
//		for (int z = 0; z < 10; z++)
//			System.out.println(index[z]);
		
		videoSource = new VideoCapture(target);
		videoWriter = new VideoWriter();
		
		videoSource.set(Videoio.CV_CAP_PROP_FRAME_WIDTH, 1920);
		videoSource.set(Videoio.CV_CAP_PROP_FRAME_HEIGHT, 1080);

		currentFrame = new Mat();
		videoSource.read(currentFrame);
		//videoWriter.open("D:\\Google Drive\\Lutchen Fellowship 2015\\Data\\OpenCV Calibration\\Video\\Test Video Output.avi", VideoWriter.fourcc('M','S','V','C'), 25.4, currentFrame.size());
		
		if (!videoSource.isOpened()) {
			
			try {
				Exception e = new Exception("Error opening video source.");
				throw e;
			} catch (Exception e) {
				e.printStackTrace();
			} // end try-catch
			
		} // end if
		
	} // end constructor
	
	/**
	 * Constructor that points to a video file.
	 * @param target Filepath to video file
	 */
	
	public DropletAnalyzer(String target) {
		
		videoSource = new VideoCapture(target);
		videoWriter = new VideoWriter();
		
		
		currentFrame = new Mat();
		videoSource.read(currentFrame);
		videoWriter.open("D:\\Google Drive\\Lutchen Fellowship 2015\\Data\\OpenCV Calibration\\Video\\Test Video Output.avi", VideoWriter.fourcc('M','S','V','C'), 25.4, currentFrame.size());
		
		if (!videoSource.isOpened()) {
			
			try {
				Exception e = new Exception("Error opening video source.");
				throw e;
			} catch (Exception e) {
				e.printStackTrace();
			} // end try-catch
			
		} // end if
		
	} // end constructor
	
	/*
	 * Methods
	 */

	/**
	 * Performs the analysis for the PID subsystem to act upon.
	 * 
	 * @param pid - The PID subsystem dedicated to performing this feedback analysis.
	 * @param currentFrame - the current frame from the camera.
	 * @return The frequency setpoint of the function generator.
	 */
	public double performFeedbackAnalysis(PIDSubsystem pid, Mat currentFrame) {
		
		Mat clone = new Mat();
		Mat output = new Mat();
		Rect rectangle;
		double aspRat;
		
		// Start timer
		pid.setTime();
		
		
		Imgproc.cvtColor(currentFrame, clone, Imgproc.COLOR_BGR2GRAY);
		//Imgproc.HoughCircles(clone, output, Imgproc.CV_HOUGH_GRADIENT, 1, 800);
		analyzeFrame(clone);
//		rectangle = findDropletProfile(output);
//		
//		aspRat = rectangle.x / rectangle.y;
		
		System.out.println("Aspect Ratio: " + baRatio);
		
		return pid.calculateOutput(baRatio);
		
	} // end performAnalysis
	
	/**
	 * Performs the deformation / location analysis on the droplet - i.e. finding the droplet in the image, measuring the aspect ratio and 
	 * the centroid location relative to the top of the transducer.
	 * 
	 * @param input An OpenCV Mat object from the camera's live stream containing the droplet.
	 * @return A modified Mat object that contains a bounding rectangle around the droplet from the original image. If the droplet was not found, returns the orignal image.
	 */
	public Mat analyzeFrame(Mat input) {
		
		// Error Checking
		if (input.empty())
			try {
				Exception e = new Exception("Error loading frame: frame does not exist.");
				throw e;
			} catch (Exception e) {
				e.printStackTrace();
			} // end try-catch
			
		boundingRectangle = findDropletProfile(prepFrame(input));
		
		if (boundingRectangle != null) {
			
			measureDropletAxes(boundingRectangle);
			//generateCSV();
			//input.convertTo(input, Imgproc.COLOR_BayerGR2RGB);
			input = drawRectangle(boundingRectangle, input);
			//videoWriter.write(input);
			
		} else {
			System.out.println("WARNING: TARGET NOT FOUND.");
		} // end if-else
		
		analyzedVideo.add(input);
		
		if (isCompressing) 
			analyzedCompressionVideo.add(input);
		else
			analyzedRelaxationVideo.add(input);
		
		return input;
		
	} // end analyze
	
	/**
	 * Saves the analyzed images as a video and outputs the data to a CSV file for further analysis.
	 * Evertyhing is saved to a brand-new directory determined by the user's specified save location and the local time stamp.
	 */
	public void saveData() {
		
		// Set up directory information
		filename = LocalDateTime.now().toString();
		filename = filename.replaceAll(":", "_");
		
		
		String savePath;
		
		if (path != null)
			savePath = path + "\\" + filename;
		else
			savePath = defaultPath + "\\" + filename;
			
		File directory = new File(savePath);
		directory.mkdir();
		
		// Save CSV file
		generateCSV(savePath, compressionData, "Compression Data");
		generateCSV(savePath, relaxationData, "Relaxation Data");
		
		// Save Complete Video:
		videoWriter.open(savePath + "\\Complete Video Output.avi", VideoWriter.fourcc('M','S','V','C'), 25.4, currentFrame.size());
		
		if (!analyzedVideo.isEmpty())
			for (int z = 0; z < analyzedVideo.size(); z++)
				videoWriter.write(analyzedVideo.get(z));
		
		videoWriter.release();
		analyzedVideo.clear();
		
		// Save Compression Video:
		videoWriter.open(savePath + "\\Compression Video Output.avi", VideoWriter.fourcc('M','S','V','C'), 25.4, currentFrame.size());

		if (!analyzedCompressionVideo.isEmpty())
			for (int z = 0; z < analyzedCompressionVideo.size(); z++)
				videoWriter.write(analyzedCompressionVideo.get(z));

		videoWriter.release();
		analyzedCompressionVideo.clear();
		
		// Save Compression Video:
		videoWriter.open(savePath + "\\Relaxation Video Output.avi", VideoWriter.fourcc('M','S','V','C'), 25.4, currentFrame.size());

		if (!analyzedRelaxationVideo.isEmpty())
			for (int z = 0; z < analyzedRelaxationVideo.size(); z++)
				videoWriter.write(analyzedRelaxationVideo.get(z));

		videoWriter.release();
		analyzedRelaxationVideo.clear();
		
		filename = null;
		
	} // end saveData
	
	/**
	 * Saves the analyzed images as a video and outputs the data to a CSV file for further analysis.
	 * Evertyhing is saved to a brand-new directory determined by the user's specified save location and the local time stamp.
	 */
	public void multiRunSave() {
		
		// Set up directory information if it doesn't exist
		if (filename == null) {
			
			filename = LocalDateTime.now().toString();
			filename = filename.replaceAll(":", "_");
			
		} // end if
		
		runCounter = runCounter++;
		String savePath;
		
		if (path != null)
			savePath = path + "\\" + filename;
		else
			savePath = defaultPath + "\\" + filename;
		
		File directory = new File(savePath);
		if (!directory.exists())
			directory.mkdir();
		
		// Save CSV file
		generateCSV(savePath, compressionData, "Compression Data Run " + runCounter);
		generateCSV(savePath, relaxationData, "Relaxation Data " + runCounter);
		
		// Save Complete Video:
		videoWriter.open(savePath + "\\Complete Video Output of Run " + runCounter + ".avi", VideoWriter.fourcc('M','S','V','C'), 25.4, currentFrame.size());
		
		if (!analyzedVideo.isEmpty())
			for (int z = 0; z < analyzedVideo.size(); z++)
				videoWriter.write(analyzedVideo.get(z));
		
		videoWriter.release();
		analyzedVideo.clear();
		
		// Save Compression Video:
		videoWriter.open(savePath + "\\Compression Video Output of Run " + runCounter + ".avi", VideoWriter.fourcc('M','S','V','C'), 25.4, currentFrame.size());

		if (!analyzedCompressionVideo.isEmpty())
			for (int z = 0; z < analyzedCompressionVideo.size(); z++)
				videoWriter.write(analyzedCompressionVideo.get(z));

		videoWriter.release();
		analyzedCompressionVideo.clear();
		
		// Save Compression Video:
		videoWriter.open(savePath + "\\Relaxation Video Output of Run " + runCounter + ".avi", VideoWriter.fourcc('M','S','V','C'), 25.4, currentFrame.size());

		if (!analyzedRelaxationVideo.isEmpty())
			for (int z = 0; z < analyzedRelaxationVideo.size(); z++)
				videoWriter.write(analyzedRelaxationVideo.get(z));

		videoWriter.release();
		analyzedRelaxationVideo.clear();
		
		if (runCounter == maxNumberOfRuns) {
			
			filename = null;
			runCounter = 0;
			
		} // end if
		
	} // end saveData
	
	/**
	 * Method to extract the camera from this class. Use the VideoCapture object contained within
	 * to obtain a live feed.
	 * 
	 * @return videoSource - The camera image stream.
	 */
	public VideoCapture getCamera() {
		return videoSource;
	} // end VideoSource
	
	/**
	 * Sets the output path to the specified location.
	 * @param path - A String that contains the output path.
	 */
	public void setPath(String path) {
		this.path = path;
	} // end setPath
	
	/**
	 * Takes the input frame and applies a Gaussian blur, Laplacian edge-detection algorithm and binary threshold.
	 * 
	 * Gaussian Blur Information: 
	 * * Kernal Size: 3x3
	 * * Border Condition: OpenCV Default
	 * 
	 * Laplacian Algorithm Information:
	 * * Aperature Size: 5
	 * * Scaling Factor: 1
	 * * Border Condition: OpenCV Default
	 * 
	 * Binary Threshold Information:
	 * * Minimum Threshold Value: 50
	 * * Maximum Value: 255
	 * 
	 * @param input OpenCV Matrix input frame to be prepped for futher analysis.
	 * @return A clone of the original input frame with the above operations applied.
	 */
	public Mat prepFrame(Mat input) {
		
		Mat clone = new Mat(input.size(), CvType.CV_8UC1);
		//low pass here!
		Imgproc.GaussianBlur(input, clone, new Size(3, 3), 0); // Apply Guassian blur, reduces noise.
		Imgproc.Laplacian(clone, clone, depth, 5, scale, delta, 0); // Apply Laplacian edge-finding algorithm
		Imgproc.threshold(clone, clone, 25, 255, Imgproc.THRESH_TOZERO); // Apply Binary threshold.
		
		return clone;
		
	} // end prepFrame
	
	/**
	 * Finds the droplet in the binary image and places a bounding rectangle around it. The droplet contour is assumed to be the largest contour in the binary image.
	 * @param input An OpenCV Matrix that has previously been modified with a call to prepFrame()
	 * @return The information pertaining to the bounding rectangle around the droplet's profile.
	 */
	private Rect findDropletProfile(Mat input) {
		List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
		Mat clone = input.clone();
		
		Imgproc.findContours(clone, contours, new Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
		
		if (contours.size() != 0) {
			
			/*
			 * TODO: Optimize for loop. Right now seach is linear - needs to be faster.
			 */
			double largestArea = 0;
			int index = 0;
			for (int z = 0; z < contours.size(); z++) {

				double area = Imgproc.contourArea(contours.get(z));
				if (area > largestArea) {

					largestArea = area;
					index = z;

				} // end if

			} // end for
			
			return Imgproc.boundingRect(contours.get(index)); // Uses the largest contour to make a box around the droplet.
		
		} else {
			return null;
		} // end if-else
		
	} // end findDropletProfile
	
	/**
	 * Using the bounding rectangle, the semimajor and semiminor axis are calculated. Additionally, grab the centroid x and y coordinates.
	 * 
	 * @param boundingRectangle The bounding rectangle around the droplet profile.
	 */
	private void measureDropletAxes(Rect boundingRectangle) {
		
		double a = boundingRectangle.height / 2;
		double b = boundingRectangle.width / 2;
		
		baRatio = b/a;
		
		
		double dropletLocation = ((1080 - (boundingRectangle.y + a))/ calibrationConstant); // + heightAboveHorn; // Need to subtract centroid location since the coordinate system in the image is with +Y downwards. I know, weird.
		double locRelFrame = 1080 - (boundingRectangle.y + a); //dropletLocation - (nodeLocation + heightAboveHorn);
		double locRelStart;
		
		if (!startLocationMeasured) {
			
			startLocation = dropletLocation;
			locRelStart = 0;
			startLocationMeasured = true;
			
		} else
			locRelStart = dropletLocation - startLocation;
		
		double[] output = {a, b, baRatio, dropletLocation, locRelFrame, locRelStart}; // Data is stored as [a, b, a/b, height, LocRelFrame, locRelStart]
		
		x = 1080 - (boundingRectangle.x + b);
		y = locRelFrame;
		
		if (debugMode) {
			
			System.out.println("Axis A: " + output[0] + " microns");
			System.out.println("Axis B: " + output[1] + " microns");
			System.out.println("COG height above horn: " + dropletLocation + " microns");
			System.out.println("COG Height relative to Edge of Frame: " + locRelFrame + " pixels");
		
		} // end if
		
		if (isCompressing)
			compressionData.add(output);
		else
			relaxationData.add(output);
		
	} // end measureDropletAxes
	
	/**
	 * Draws the bounding rectangle around the droplet profile. This is mostly for sanity-checking the analysis algorithm.
	 * @param boundingRectangle - The box around the droplet's profile
	 * @param input - The source image. A clone of this frame is made; all modifications are done to the clone. 
	 * @return A clone of the input frame with the bounding box around the dropplet.
	 */
	private Mat drawRectangle(Rect boundingRectangle, Mat input) {
		
		Mat clone = input.clone();
		
		// Convert image to RGB colorspace.
		clone.convertTo(clone, Imgproc.COLOR_GRAY2BGR);
		Imgproc.rectangle(clone, new Point(boundingRectangle.x, boundingRectangle.y), new Point(boundingRectangle.x + boundingRectangle.width, boundingRectangle.y + boundingRectangle.height), new Scalar(0, 255, 0), 1);
		
		return clone;
		
	} // end drawRectangle
	
	/**
	 * Writes the data generated by measureDropletAxes to a CSV File for analysis in another software package. This particular method is for external testing purposes only
	 * as the directory is set to D:\Google Drive\Lutchen Fellowship 2015\Data\OpenCV Calibration.
	 */
	public void generateCSV(ArrayList<double[]> data) {
		
		try {
			
			CSVWriter csvOutput = new CSVWriter(new FileWriter("D:\\Google Drive\\Lutchen Fellowship 2015\\Data\\OpenCV Calibration\\Data.csv"), '\t', ',');
			String[] entry = "A (microns)#B (microns)#B/A#Height Above Horn (microns)#Height Relative to Edge of Frame (pixels)".split("#");
			csvOutput.writeNext(entry);
			
			for (int z = 0; z < data.size(); z++) {
				
				for (int s = 0; s < 5; s++) 
					entry[s] = Double.toString(data.get(z)[s]);
				
				csvOutput.writeNext(entry);
				
			} // end for
			
			csvOutput.close();
			System.out.println("CSV File has been generated.");
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} // end try-catch
		
	} // end generateCSV
	
	/**
	 * Writes the data generated by measureDropletAxes to a CSV File for analysis in another software package.
	 * 
	 * @param path - Directory location in which the CSV file is generated.
	 */
	private void generateCSV(String path, ArrayList<double[]> data, String modifier) {
		
		try {
			
			File csvFile = new File(path + "\\" + modifier + ".csv");
			csvFile.createNewFile();
			CSVWriter csvOutput = new CSVWriter(new FileWriter(csvFile.getAbsolutePath()), '\t', ',');
			String[] entry = "A (microns)#B (microns)#B/A#Height Above Horn (microns)#Height Relative to Edge of Frame (pixels)#Height Relative to Starting Position".split("#");
			csvOutput.writeNext(entry);
			
			for (int z = 0; z < data.size(); z++) {
				
				for (int s = 0; s < 5; s++) 
					entry[s] = Double.toString(data.get(z)[s]);
				
				csvOutput.writeNext(entry);
				
			} // end for
			
			csvOutput.close();
			System.out.println("CSV File has been generated.");
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} // end try-catch
		
		endTime = System.currentTimeMillis();
		data.clear();
		startLocationMeasured = false;
		System.out.println("Total execution time: " + (endTime - startTime) + " milliseconds.");
		
	} // end generateCSV
	
	/**
	 * Starts the system timer to time how long the total analysis takes.
	 */
	public void beginTimer() {
		
		startTime = System.currentTimeMillis();
		
	} // end beginTimer
	
	/**
	 * Hand-off function to get the isCompressing flag from the Agilent Function Generator.
	 * 
	 * @param compressing - Chain the output of the AgilentFunctionGenerator method "isCompressing" here.
	 */
	public void checkSweepingStatus(boolean compressing) {
		
		if (compressing)
			isCompressing = true;
		else
			isCompressing = false;
		
	} // end checkSweepingStatus
	
	/**
	 * Gets the centroid location on the x axis.
	 * 
	 * @return The centroid location on the x axis.
	 */
	public double getCentroidX() {
		return x;
	} // end getCentroidX
	
	/**
	 * Gets the centroid location on the y axis.
	 * 
	 * @return The centroid location on the y axis.
	 */
	public double getCentroidY() {
		return y;
	} // end getCentroidX
	
	/**
	 * Grabs the aspect ratio of the droplet.
	 * 
	 * @return The aspect ratio of the droplet.
	 */
	public double getAspectRatio() {
		return baRatio;
	} // end getAspectRatio
	
	
	/**
	 * Sets the maximum number of runs for a multi-run analysis.
	 * @param runs - The number of data runs desired.
	 */
	public void setMaxRuns(int runs) {
		maxNumberOfRuns = runs;
	} // end setMaxRuns
	
} // end DropletAnalyzer













