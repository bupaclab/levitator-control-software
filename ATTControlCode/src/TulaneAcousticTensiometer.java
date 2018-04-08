/**
 * 
 */

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.ParseException;

import javax.swing.JFrame;
import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import java.awt.Component;
import javax.swing.border.EtchedBorder;
import java.awt.GridLayout;
import java.awt.Color;
import java.awt.SystemColor;
import javax.swing.border.BevelBorder;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.LayoutStyle.ComponentPlacement;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ActionEvent;
import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import javax.swing.SpinnerNumberModel;
import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.JToggleButton;
import java.awt.event.ItemListener;
import javax.swing.JCheckBox;

/**
 * @author Jarrod Risley
 *
 */
public class TulaneAcousticTensiometer {

	/*
	 * Local Variables
	 */
	private JFrame frmAcousticTweezingTensiometer;
	private JPanel panel;
	private JPanel dropletInfoPanel;
	private Graphics g;
	private JFileChooser fileChooser;
	private JSpinner funcGenAmpSpinner;
	private JSpinner funcGenFreqSpinner;
	private JLabel labelX;
	private JLabel displayX;
	private JLabel displayY;
	private JLabel aspectRatio;
	private JCheckBox elasticityCheckBox;
	
	private DropletAnalyzer analyzer;
	private AgilentFunctionGenerator agilent;
	private VideoCapture baslerCamera;
	private PIDSubsystem pidSubsystem;
	private Mat currentFrame;
	private Mat returnFrame;
	private MatOfByte byteFrame;
	private LiveFeedThread cameraFeed;
	private AnalyzerThread frameAnalyzer;
	private FunctionGeneratorThread funcGenThread;
	private PIDAspectRatioThread pidThread;
	private ElasticityAnalyzerThread elasticityThread;
	private ExecutorService executor;
	
	private static final int cameraLocation = 0;
	private boolean runElasticityAnalysis = false;
	private boolean wasPIDEngaged = false;
	private String path;
	
	private final double defaultP = 1;
	private final double defaultI = 0;
	private final double defaultD = 5;
	
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					TulaneAcousticTensiometer window = new TulaneAcousticTensiometer();
					window.frmAcousticTweezingTensiometer.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		
	} // end main
	
	/**
	 * Create the application.
	 */
	public TulaneAcousticTensiometer() {
		initialize();
	} // end Constructor
	
	private void initialize() {
		
		/*
		 * Initialize Instruments
		 */
		analyzer = new DropletAnalyzer(cameraLocation);
		agilent = new AgilentFunctionGenerator();
		baslerCamera = analyzer.getCamera();
		
		pidSubsystem = new PIDSubsystem(defaultP, defaultI, defaultD);
			pidSubsystem.setSetpoint(1.2); // SET SETPOINT HERE
		
		/*
		 * Initialize Frame
		 */
		frmAcousticTweezingTensiometer = new JFrame();
		frmAcousticTweezingTensiometer.setTitle("Acoustic Tweezing Tensiometer");
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		frmAcousticTweezingTensiometer.setBounds(0, 0, screen.width, screen.height - 30);
		frmAcousticTweezingTensiometer.setExtendedState(Frame.MAXIMIZED_BOTH);
		frmAcousticTweezingTensiometer.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		panel = new JPanel();
		panel.setLayout(null);
		panel.setBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null));
		panel.setBackground(SystemColor.info);
		
		JButton btnStart = new JButton("Start");
		btnStart.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				
				// Start Droplet Analysis
//				runAnalysis = true;
				
				if (!elasticityCheckBox.isSelected()) {
					
					executor.execute(funcGenThread);
					executor.execute(frameAnalyzer);

				} else {
					
					executor.execute(elasticityThread);
					agilent.setSweeping(true);
					
				} // end if-else
				
			} // end actionPerformed
		});
		
		JButton btnStop = new JButton("Stop");
		btnStop.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				frameAnalyzer.runnable = false;
			} // end actionPeformed
		});
		
		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.BOTTOM);
		
		dropletInfoPanel = new JPanel();
		dropletInfoPanel.setBorder(new BevelBorder(BevelBorder.LOWERED, null, null, null, null));
		
		JToggleButton tglbtnEngagePidSubsystem = new JToggleButton("Engage PID Subsystem");
		tglbtnEngagePidSubsystem.addItemListener(new ItemListener() {
			
			public void itemStateChanged(ItemEvent arg0) {
				
				if(arg0.getStateChange() == ItemEvent.SELECTED)
					pidThread.runnable = true;
				else if (arg0.getStateChange() == ItemEvent.DESELECTED)
					pidThread.runnable = false;
				
			} // itemStateChanged
			
		}); // end addItemListener
		
		elasticityCheckBox = new JCheckBox("Run Elasticity Analysis");
		
		GroupLayout groupLayout = new GroupLayout(frmAcousticTweezingTensiometer.getContentPane());
		groupLayout.setHorizontalGroup(
			groupLayout.createParallelGroup(Alignment.LEADING)
				.addGroup(groupLayout.createSequentialGroup()
					.addGroup(groupLayout.createParallelGroup(Alignment.LEADING, false)
						.addGroup(groupLayout.createSequentialGroup()
							.addGap(68)
							.addComponent(dropletInfoPanel, GroupLayout.PREFERRED_SIZE, 324, GroupLayout.PREFERRED_SIZE)
							.addGap(54)
							.addComponent(panel, GroupLayout.PREFERRED_SIZE, 1024, GroupLayout.PREFERRED_SIZE)
							.addGap(18))
						.addGroup(groupLayout.createSequentialGroup()
							.addGap(538)
							.addComponent(btnStart)
							.addGap(135)
							.addComponent(elasticityCheckBox)
							.addPreferredGap(ComponentPlacement.RELATED, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
							.addComponent(tglbtnEngagePidSubsystem)
							.addGap(212)
							.addComponent(btnStop)
							.addGap(71)))
					.addComponent(tabbedPane, GroupLayout.DEFAULT_SIZE, 416, Short.MAX_VALUE))
		);
		groupLayout.setVerticalGroup(
			groupLayout.createParallelGroup(Alignment.LEADING)
				.addGroup(groupLayout.createSequentialGroup()
					.addGap(220)
					.addComponent(panel, GroupLayout.PREFERRED_SIZE, 576, GroupLayout.PREFERRED_SIZE)
					.addPreferredGap(ComponentPlacement.RELATED, 87, Short.MAX_VALUE)
					.addGroup(groupLayout.createParallelGroup(Alignment.BASELINE)
						.addComponent(btnStart)
						.addComponent(btnStop)
						.addComponent(tglbtnEngagePidSubsystem)
						.addComponent(elasticityCheckBox))
					.addGap(85))
				.addGroup(groupLayout.createSequentialGroup()
					.addComponent(tabbedPane, GroupLayout.PREFERRED_SIZE, 955, GroupLayout.PREFERRED_SIZE)
					.addContainerGap(36, Short.MAX_VALUE))
				.addGroup(groupLayout.createSequentialGroup()
					.addGap(265)
					.addComponent(dropletInfoPanel, GroupLayout.PREFERRED_SIZE, 409, GroupLayout.PREFERRED_SIZE)
					.addContainerGap(317, Short.MAX_VALUE))
		);
		dropletInfoPanel.setLayout(null);
		
		displayX = new JLabel("0");
		displayX.setBounds(126, 51, 46, 14);
		dropletInfoPanel.add(displayX);
		
		displayY = new JLabel("0");
		displayY.setBounds(126, 75, 46, 14);
		dropletInfoPanel.add(displayY);
		
		labelX = new JLabel("X:"); 
		labelX.setBounds(116, 51, 46, 14);
		dropletInfoPanel.add(labelX);
		
		JLabel labelY = new JLabel("Y:");
		labelY.setBounds(116, 75, 46, 14);
		dropletInfoPanel.add(labelY);
		
		JLabel aspectLabel = new JLabel("Aspect Ratio: ");
		aspectLabel.setHorizontalAlignment(SwingConstants.CENTER);
		aspectLabel.setBounds(10, 103, 116, 27);
		dropletInfoPanel.add(aspectLabel);
		
		aspectRatio = new JLabel("0.0");
		aspectLabel.setLabelFor(aspectRatio);
		aspectRatio.setBounds(136, 109, 142, 14);
		dropletInfoPanel.add(aspectRatio);
		
		JPanel functGenTab = new JPanel();
		tabbedPane.addTab("Function Generator", null, functGenTab, null);
		functGenTab.setLayout(null);
		
		funcGenFreqSpinner = new JSpinner();
		funcGenFreqSpinner.setValue(new Double(agilent.getFrequency()));
		funcGenFreqSpinner.addChangeListener(new ChangeListener() {
			
			public void stateChanged(ChangeEvent arg0) {
				
				if (agilent == null)
					System.out.println("I done goofed.");
				
				try {
					
					funcGenFreqSpinner.commitEdit();
					agilent.setFrequecy((int) funcGenFreqSpinner.getValue());
					
				} catch (ParseException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} // end try-catch
				
			} // end stateChanged
			
		}); // end addChangeListener
		
		funcGenFreqSpinner.setToolTipText("Set the frequency of the function generator here.");
		funcGenFreqSpinner.setBounds(150, 76, 152, 20);
		functGenTab.add(funcGenFreqSpinner);
		
		funcGenAmpSpinner = new JSpinner();
		funcGenAmpSpinner.setFont(new Font("Tahoma", Font.PLAIN, 11));
		funcGenAmpSpinner.setModel(new SpinnerNumberModel(new Double(0), null, null, new Double(0)));
		funcGenAmpSpinner.setValue(new Double(agilent.getAmplitude()));
		funcGenAmpSpinner.addChangeListener(new ChangeListener() {
			
			public void stateChanged(ChangeEvent e) {
				
				if (agilent == null)
					System.out.println("I done goofed.");
				
				try {
					
					funcGenAmpSpinner.commitEdit();
					agilent.setAmplitude((double) funcGenAmpSpinner.getValue());
					
				} catch (ParseException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} // end try-catch
				
			} // end stateChanged
			
		}); // end addChangeListener
		funcGenAmpSpinner.setToolTipText("Use this to set the amplitude of the function generator.");
		funcGenAmpSpinner.setBounds(150, 122, 152, 20);
		functGenTab.add(funcGenAmpSpinner);
		
		JPanel baslerTab = new JPanel();
		tabbedPane.addTab("Camera", null, baslerTab, null);
		frmAcousticTweezingTensiometer.getContentPane().setLayout(groupLayout);
		
		JMenuBar menuBar = new JMenuBar();
		frmAcousticTweezingTensiometer.setJMenuBar(menuBar);
		
		JMenu mnFile = new JMenu("File");
		menuBar.add(mnFile);
		
		JMenuItem mntmSetWorkingDirectory = new JMenuItem("Set Working Directory");
		mntmSetWorkingDirectory.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				
				fileChooser = new JFileChooser("Set Working Directory");
				fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				if(fileChooser.showOpenDialog(frmAcousticTweezingTensiometer) == JFileChooser.APPROVE_OPTION) {
					
					path = fileChooser.getSelectedFile().getAbsolutePath();
					analyzer.setPath(path);
					System.out.println("Path is currently set to: " + path);
					
				} // end if
				
			} // end actionPerformed
		}); // end addActionListener
		mnFile.add(mntmSetWorkingDirectory);
		
		
		/*
		 * Initialize Matricies
		 */
		currentFrame = new Mat();
		returnFrame = new Mat();
		byteFrame = new MatOfByte();
		
		/*
		 * Initialize Threads
		 */
		
		executor = Executors.newFixedThreadPool(5);
			cameraFeed = new LiveFeedThread();
			cameraFeed.runnable = true;
			frameAnalyzer = new AnalyzerThread();
			frameAnalyzer.runnable = true;
			funcGenThread = new FunctionGeneratorThread();
			funcGenThread.runnable = true;
			pidThread = new PIDAspectRatioThread();
			elasticityThread = new ElasticityAnalyzerThread();
			elasticityThread.runnable = true;
			executor.execute(pidThread);
			executor.execute(cameraFeed);
			
		
	} // end initializeApplication
	
	/**
	 * Thread class that is dedicated to handling the live video feed from the apparatus.
	 * @author Jarrod Risley
	 *
	 */
	private class LiveFeedThread implements Runnable {
		
		protected volatile boolean runnable = false;

		@Override
		public void run() {
			
			synchronized(this) {
				
				while(runnable) {
					
					if(baslerCamera.grab()) {

						try {
							
							if (panel.getWidth() > 0 || panel.getHeight() > 0) {
							
								baslerCamera.read(currentFrame);
								Size sz = new Size(panel.getWidth(), panel.getHeight());
								Mat clone = new Mat();
								Imgproc.resize((agilent.isSweeping() && returnFrame.size().area() != 0) ? returnFrame:currentFrame, clone, sz, 0, 0, Imgproc.INTER_AREA);
//								Imgcodecs.imencode(".bmp", currentFrame, byteFrame);
//								clone.release();
//								Image im = ImageIO.read(new ByteArrayInputStream(byteFrame.toArray()));

								BufferedImage buff = (BufferedImage) toBufferedImage(clone);
								//im.flush();
								g = panel.getGraphics();
								g.drawImage(buff, 0, 0, panel.getWidth(), panel.getHeight(), 0, 0, buff.getWidth(), buff.getHeight(), null);
								buff.flush();

								if (runnable == false) 
								{
									System.out.println("Going to Wait...");
									this.wait();
								} // end if
							
							} // end if
							
						} catch(Exception e) {
							System.out.println(e.getMessage());
						} // end try-catch
						
					} // end if
					
				} // end while
			} // end syncrhonized
			
		} // end run
		
		
	} // end liveFeedThread
	
	/**
	 * Thread class that specifically deals with analyzing images from the camera stream.
	 * @author Jarrod Risley
	 *
	 */
	private class AnalyzerThread implements Runnable {

		protected volatile boolean runnable = false;
		
		@Override
		public void run() {
			
			synchronized(this) {
					
				analyzer.beginTimer();
				
				while(runnable && agilent.isSweeping()) {
				
					Mat analysisFrame = new Mat();
					Imgproc.cvtColor(currentFrame, analysisFrame, Imgproc.COLOR_BGR2GRAY);
					returnFrame =  analyzer.analyzeFrame(analysisFrame);
					analyzer.checkSweepingStatus(agilent.isCompressing());
					
					changeLabel(displayX, Double.toString(analyzer.getCentroidX()));
					changeLabel(displayY, Double.toString(analyzer.getCentroidY()));
					changeLabel(aspectRatio, Double.toString(analyzer.getAspectRatio()));
					dropletInfoPanel.repaint();
					
				} // end while
				
				if (runnable)
					analyzer.saveData();
				
			}// end synchronized
			
		} // end run
		
	} // end Analyzer Thread
	
	/**
	 * Thread class that specifically deals with Kevin's elasticity measurements.
	 * @author Jarrod Risley
	 *
	 */
	private class ElasticityAnalyzerThread implements Runnable {

		protected volatile boolean runnable = false;
		
		@Override
		public void run() {
			
			synchronized(this) {
					
				analyzer.setMaxRuns(11);
				analyzer.beginTimer();
				
				double height = 0;
				double delta = 0.005; // volts
				double startAmplitude = agilent.getAmplitude();
				double endAmplitude;
				
				while(runnable && agilent.isSweeping() && analyzer.getAspectRatio() < 1.3) {
				
					Mat analysisFrame = new Mat();
					Imgproc.cvtColor(currentFrame, analysisFrame, Imgproc.COLOR_BGR2GRAY);
					returnFrame =  analyzer.analyzeFrame(analysisFrame);
					agilent.sweepTo(agilent.getAmplitude(), agilent.getAmplitude() + delta, false);
					
					changeLabel(displayX, Double.toString(analyzer.getCentroidX()));
					changeLabel(displayY, Double.toString(analyzer.getCentroidY()));
					changeLabel(aspectRatio, Double.toString(analyzer.getAspectRatio()));
					dropletInfoPanel.repaint();
					
					height = analyzer.getCentroidY();
					
				} // end while
				
				endAmplitude = agilent.getAmplitude();
				agilent.sweepTo(endAmplitude, startAmplitude, true);
	
				if (runnable)
					analyzer.multiRunSave();
				
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} // end try-catch
				
				for (int z = 0; z < 11; z++) {
					
					while (runnable && agilent.isSweeping() && agilent.getAmplitude() < endAmplitude) {
						
						Mat analysisFrame = new Mat();
						Imgproc.cvtColor(currentFrame, analysisFrame, Imgproc.COLOR_BGR2GRAY);
						returnFrame =  analyzer.analyzeFrame(analysisFrame);
						agilent.sweepTo(agilent.getAmplitude(), agilent.getAmplitude() + delta, false);
						
						changeLabel(displayX, Double.toString(analyzer.getCentroidX()));
						changeLabel(displayY, Double.toString(analyzer.getCentroidY()));
						changeLabel(aspectRatio, Double.toString(analyzer.getAspectRatio()));
						dropletInfoPanel.repaint();
						
					} // end while
					
					agilent.sweepTo(endAmplitude, startAmplitude, true);
					
					if (runnable)
						analyzer.multiRunSave();
					
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} // end try-catch
					
				} // end for
				
				agilent.setSweeping(false);
				
			}// end synchronized
			
		} // end run
		
	} // end Analyzer Thread
	
	/**
	 * Thread class that specifically deals with controlling the function generator.
	 * @author Jarrod Risley
	 *
	 */
	private class FunctionGeneratorThread implements Runnable {

		protected volatile boolean runnable = false;
		
		@Override
		public void run() {
			
			synchronized(this) {
				
						
				agilent.setSweeping(true);
				agilent.amplitudeSweep();


				while(runnable && agilent.isSweeping()) {

					analyzer.checkSweepingStatus(agilent.isCompressing());
					funcGenFreqSpinner.setValue((int) agilent.getFrequency());
					funcGenAmpSpinner.setValue(agilent.getAmplitude());

					//					changeLabel(displayX, Double.toString(analyzer.getCentroidX()));
					//					changeLabel(displayY, Double.toString(analyzer.getCentroidY()));
					//					changeLabel(aspectRatio, Double.toString(analyzer.getAspectRatio()));
					//					dropletInfoPanel.repaint();

				} // end inner while
				
			}// end synchronized
			
		} // end run
		
	} // end FunctionGeneratorThread
	
	/**
	 * Thread class that specifically deals with controlling the PID subsystem in charge of maintaining the droplet's aspect ratio.
	 * @author Jarrod Risley
	 *
	 */
	private class PIDAspectRatioThread implements Runnable {

		protected volatile boolean runnable = false;
		protected double pidOutput;
		
		@Override
		public void run() {
			
			synchronized(this) {
								
				while (true) {
					
					while(runnable && !agilent.isSweeping()) {

						pidOutput = analyzer.performFeedbackAnalysis(pidSubsystem, currentFrame);
						System.out.println(pidOutput); // replace this line once I know what the hell the PID subsystem does xD
						agilent.setFrequecy(agilent.getFrequency() + pidOutput);

					} // end inner while
				
				} // end outer while
				
			}// end synchronized
			
		} // end run
		
	} // end FunctionGeneratorThread
	
	public Image toBufferedImage(Mat m){
		
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if ( m.channels() > 1 ) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        int bufferSize = m.channels()*m.cols()*m.rows();
        byte [] b = new byte[bufferSize];
        m.get(0,0,b); // get all the pixels
        BufferedImage image = new BufferedImage(m.cols(),m.rows(), type);
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(b, 0, targetPixels, 0, b.length);  
        return image;
    }
	
	private void  changeLabel(final JLabel label, final String text) {
		label.setText(text);
	}
} // end Acoustic Tensiometer
