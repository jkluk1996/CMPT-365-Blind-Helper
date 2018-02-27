package application;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import utilities.Utilities;

public class Controller {
	
	@FXML
	private ImageView imageView; // the image display window in the GUI
	@FXML
	private Slider slider;
	
	
	private Mat image;
	private VideoCapture capture;
	private ScheduledExecutorService timer;

	private ScheduledFuture<?> scheduledFuture;
	
	private int width;
	private int height;
	private static int sampleRate; // sampling frequency
	private int sampleSizeInBits;
	private int numberOfChannels;
	private double[] freq; // frequencies for each particular row
	private int numberOfQuantizionLevels;
	private int numberOfSamplesPerColumn;
	
	private double framePerSecond;
	private int clickCounter;
	private Boolean playing = false;
	@FXML
	private Button playBtn;
	
	@FXML
	private void initialize() {
		width = 64;
		height = 64;
		sampleRate = 8000;
		sampleSizeInBits = 8;
		numberOfChannels = 1;
		
		numberOfQuantizionLevels = 16;
		
		numberOfSamplesPerColumn = 500;
		
		// assign frequencies for each particular row
		freq = new double[height]; // Be sure you understand why it is height rather than width
		freq[height/2-1] = 440.0; // 440KHz - Sound of A (La)
		for (int m = height/2; m < height; m++) {
			freq[m] = freq[m-1] * Math.pow(2, 1.0/12.0); 
		}
		for (int m = height/2-2; m >=0; m--) {
			freq[m] = freq[m+1] * Math.pow(2, -1.0/12.0); 
		}
		
		// Added events for reacting to mouse presses on the slider to update video
		slider.setOnMousePressed((MouseEvent event) -> {
			if (capture != null) {
				scheduledFuture.cancel(true);
			}
		});
		
		slider.setOnMouseReleased((MouseEvent event) -> {
			if (capture != null) {
				double sliderPosition = slider.getValue();
				if (sliderPosition == slider.getMax()) {
					capture.set(Videoio.CAP_PROP_POS_FRAMES, 0);
				}
				else {
					double totalFrameCount = capture.get(Videoio.CAP_PROP_FRAME_COUNT);
					double videoFrame = sliderPosition*(totalFrameCount) / (slider.getMax() - slider.getMin());
					capture.set(Videoio.CAP_PROP_POS_FRAMES, videoFrame);
				}
				
				Mat frame = new Mat();
				capture.read(frame);
				Image im = Utilities.mat2Image(frame);
				Utilities.onFXThread(imageView.imageProperty(), im);
				
				if (playing) {
					scheduledFuture = timer.scheduleAtFixedRate(soundPlayer, 0, Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);
				}
			}
		});
	}
	
	private String getImageFilename() {
		// This method should return the filename of the image to be played
		// You should insert your code here to allow user to select the file
		JFileChooser chooser = new JFileChooser();
		FileNameExtensionFilter mp4filter = new FileNameExtensionFilter("mp4 files (*.mp4)", "mp4");
		chooser.setFileFilter(mp4filter);		
		int result = chooser.showOpenDialog(null);
		
		if (result == JFileChooser.APPROVE_OPTION) {
			File file = chooser.getSelectedFile();
			return file.getAbsolutePath();
		} else if (result == JFileChooser.CANCEL_OPTION) {
		    System.out.println("Cancel was selected");
		    return null;
		}
		return null;
	}
	
	@FXML
	protected void openImage(ActionEvent event) throws InterruptedException {
		// This method opens an image and display it using the GUI
		// You should modify the logic so that it opens and displays a video
		// Uses JFileChooser to open the selected mp4 file
		String fileName = getImageFilename();
		if (fileName != null) {
			capture = new VideoCapture(fileName); // open video file
			if (capture.isOpened()) { // open successfully
				createFrameGrabber();
				clickCounter = 0;
				playBtn.setText("Play");
				playing = false;
			}
		}
		// You don't have to understand how mat2Image() works. 
		// In short, it converts the image from the Mat format to the Image format
		// The Mat format is used by the opencv library, and the Image format is used by JavaFX
		// BTW, you should be able to explain briefly what opencv and JavaFX are after finishing this assignment
	}

	@FXML
	protected void playImage(ActionEvent event) throws LineUnavailableException {
		// This method "plays" the image opened by the user
		// You should modify the logic so that it plays a video rather than an image
		
		// I used an AudioFormat object and a SourceDataLine object to perform audio output. Feel free to try other options
		if (capture != null && !playing) {
			scheduledFuture.cancel(true);
			playBtn.setText("Pause");
			playing = true;
			clickCounter++;
			
			image = new Mat();
			capture.read(image);
			// convert the image from RGB to grayscale
			Mat grayImage = new Mat();
			Imgproc.cvtColor(image, grayImage, Imgproc.COLOR_BGR2GRAY);
			
			// resize the image
			Mat resizedImage = new Mat();
			Imgproc.resize(grayImage, resizedImage, new Size(width, height));
			
			// quantization
			double[][] roundedImage = new double[resizedImage.rows()][resizedImage.cols()];
			for (int row = 0; row < resizedImage.rows(); row++) {
				for (int col = 0; col < resizedImage.cols(); col++) {
					roundedImage[row][col] = (double)Math.floor(resizedImage.get(row, col)[0]/numberOfQuantizionLevels) / numberOfQuantizionLevels;
				}
			}
			
			// I used an AudioFormat object and a SourceDataLine object to perform audio output. Feel free to try other options
	        AudioFormat audioFormat = new AudioFormat(sampleRate, sampleSizeInBits, numberOfChannels, true, true);

            SourceDataLine sourceDataLine = AudioSystem.getSourceDataLine(audioFormat);
            sourceDataLine.open(audioFormat, sampleRate);
            sourceDataLine.start();
            
            for (int col = 0; col < width; col++) {
            	byte[] audioBuffer = new byte[numberOfSamplesPerColumn];
            	for (int t = 1; t <= numberOfSamplesPerColumn; t++) {
            		double signal = 0;
                	for (int row = 0; row < height; row++) {
                		int m = height - row - 1; // Be sure you understand why it is height rather width, and why we subtract 1 
                		int time = t + col * numberOfSamplesPerColumn;
                		double ss = Math.sin(2 * Math.PI * freq[m] * (double)time/sampleRate);
                		signal += roundedImage[row][col] * ss;
                	}
                	double normalizedSignal = signal / height; // signal: [-height, height];  normalizedSignal: [-1, 1]
                	audioBuffer[t-1] = (byte) (normalizedSignal*0x7F); // Be sure you understand what the weird number 0x7F is for
            	}
            	sourceDataLine.write(audioBuffer, 0, numberOfSamplesPerColumn);
            }
            sourceDataLine.drain();
            sourceDataLine.close();
   		 
            scheduledFuture = timer.scheduleAtFixedRate(soundPlayer, 0, Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);
            }
		else if (capture != null && playing) {
			scheduledFuture.cancel(true);
			clickCounter = 0;
			playing = false;
			playBtn.setText("Play");
		}
		else {
			Exception e = new Exception("No Video Exception");
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));

			Alert alert = new Alert(Alert.AlertType.ERROR);
			alert.setHeaderText("Please open a video to play!");
			alert.getDialogPane().setExpandableContent(new ScrollPane(new TextArea(sw.toString())));
			alert.showAndWait();
		}
	} 

	protected void createFrameGrabber() throws InterruptedException {
		 if (capture != null && capture.isOpened()) { // the video must be open
			 framePerSecond = capture.get(Videoio.CAP_PROP_FPS);
		 
			 // create a runnable to fetch new frames periodically
			 Runnable frameGrabber = new Runnable() {
				 @Override
				 public void run() {
					 Mat frame = new Mat();
					 if (capture.read(frame)) { // decode successfully
						 Image im = Utilities.mat2Image(frame);
						 Utilities.onFXThread(imageView.imageProperty(), im);
						 double currentFrameNumber = capture.get(Videoio.CAP_PROP_POS_FRAMES);
						 double totalFrameCount = capture.get(Videoio.CAP_PROP_FRAME_COUNT);
						 slider.setValue(currentFrameNumber / totalFrameCount * (slider.getMax() - slider.getMin()));
					 } else { // reach the end of the video
						 capture.set(Videoio.CAP_PROP_POS_FRAMES, 0);
					 }
				 }
			 };
			
		 // terminate the timer if it is running
		 if (timer != null && !timer.isShutdown()) {
			 timer.shutdown();
			 timer.awaitTermination(Math.round(1000/framePerSecond),
			 TimeUnit.MILLISECONDS);
		 }
		 // run the frame grabber
		 timer = Executors.newSingleThreadScheduledExecutor();
		 scheduledFuture = timer.scheduleAtFixedRate(frameGrabber, 0, Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);
		 }
	}
	
	// Added Runnable for the repeated blind helper sound to be played
	Runnable soundPlayer = new Runnable() {
		 @Override
		 public void run() {
			 clickCounter++;
			 Mat frame = new Mat();
			 if (capture.read(frame)) { // decode successfully
				 Image im = Utilities.mat2Image(frame);
				 Utilities.onFXThread(imageView.imageProperty(), im);
				 double currentFrameNumber = capture.get(Videoio.CAP_PROP_POS_FRAMES);
				 double totalFrameCount = capture.get(Videoio.CAP_PROP_FRAME_COUNT);
				 slider.setValue(currentFrameNumber / totalFrameCount * (slider.getMax() - slider.getMin()));
			 } else { // reach the end of the video
				 capture.set(Videoio.CAP_PROP_POS_FRAMES, 0);
				 capture.read(frame);
			 }
			 
			// convert the image from RGB to grayscale
			Mat grayImage = new Mat();
			Imgproc.cvtColor(frame, grayImage, Imgproc.COLOR_BGR2GRAY);
			
			// resize the image
			Mat resizedImage = new Mat();
			Imgproc.resize(grayImage, resizedImage, new Size(width, height));
			
			// quantization
			double[][] roundedImage = new double[resizedImage.rows()][resizedImage.cols()];
			for (int row = 0; row < resizedImage.rows(); row++) {
				for (int col = 0; col < resizedImage.cols(); col++) {
					roundedImage[row][col] = (double)Math.floor(resizedImage.get(row, col)[0]/numberOfQuantizionLevels) / numberOfQuantizionLevels;
				}
			}
			
			// I used an AudioFormat object and a SourceDataLine object to perform audio output. Feel free to try other options
	        AudioFormat audioFormat = new AudioFormat(sampleRate, sampleSizeInBits, numberOfChannels, true, true);

            SourceDataLine sourceDataLine;
			try {
				sourceDataLine = AudioSystem.getSourceDataLine(audioFormat);
				sourceDataLine.open(audioFormat, sampleRate);
				sourceDataLine.start();
				
				for (int col = 0; col < width; col++) {
	            	byte[] audioBuffer = new byte[numberOfSamplesPerColumn];
	            	for (int t = 1; t <= numberOfSamplesPerColumn; t++) {
	            		double signal = 0;
	                	for (int row = 0; row < height; row++) {
	                		int m = height - row - 1; // Be sure you understand why it is height rather width, and why we subtract 1 
	                		int time = t + col * numberOfSamplesPerColumn;
	                		double ss = Math.sin(2 * Math.PI * freq[m] * (double)time/sampleRate);
	                		signal += roundedImage[row][col] * ss;
	                	}
	                	double normalizedSignal = signal / height; // signal: [-height, height];  normalizedSignal: [-1, 1]
	                	audioBuffer[t-1] = (byte) (normalizedSignal*0x7F); // Be sure you understand what the weird number 0x7F is for
	            	}
	            	sourceDataLine.write(audioBuffer, 0, numberOfSamplesPerColumn);
	            }
				if (clickCounter == 2) {
					double[] click = new double[numberOfSamplesPerColumn];
	    			byte[] buff = new byte[numberOfSamplesPerColumn];
	    			for (int t = 1; t <= numberOfSamplesPerColumn; t++) {
	    				click[t-1] = Math.sin(2 * Math.PI * 50 * (double)t/sampleRate);
	    				buff[t-1] = (byte) (click[t-1]*0x7F);
	    			}
	    			sourceDataLine.write(buff, 0, numberOfSamplesPerColumn);
		            clickCounter = 0;
				}
	            sourceDataLine.drain();
	            sourceDataLine.close();
	            }
			catch (LineUnavailableException e) {
				e.printStackTrace();
				}
			}
		 };

}


