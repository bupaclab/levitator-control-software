import java.io.IOException;
import java.util.Enumeration;

import be.ac.ulb.gpib.GPIBDevice;
import be.ac.ulb.gpib.GPIBDeviceIdentifier;

/**
 * @author Jarrod Risley
 *
 */
public class AgilentFunctionGenerator {

	/*
	 * Local Class Variables
	 */
	private GPIBDevice agilent;
	private GPIBDeviceIdentifier devId;
	private Enumeration<?> devList;
	
	private final int address = 10; // Address of the function generator.
	private boolean sweepInProgress = false;
	private boolean isCompressing;
	
	/**
	 * Constructor
	 */
	public AgilentFunctionGenerator() {
		
		GPIBDeviceIdentifier.initialize("be.ac.ulb.gpib.WindowsGPIBDriver", true);
		
		devList = GPIBDeviceIdentifier.getDevices();
		
		while (devList.hasMoreElements()) {
			
			devId = (GPIBDeviceIdentifier) devList.nextElement();
			
			if (devId.getAddress() == address) {
				
				agilent = new GPIBDevice(address, devId.getDriver());
				
			} // end if
			
		} // end while
		
		if (agilent != null) {
			
			try {
				
				agilent.open();
				agilent.getVendor();
				
			} catch (IOException e) {
				e.printStackTrace();
			} // end try-catch
			
		} // end if
		
	} // end constructor
	
	/**
	 * Basic, untriggered sweep. Still in beta, use at own risk :P
	 */
	public void amplitudeSweep() {
		
		if (agilent == null)
			System.out.println("I done goofed.");
		
		try {
			
			isCompressing = true;
			for (double z = 0.400; z <= 0.475; z = z + 0.00001)
				agilent.writeCommand("voltage " + z + " VPP");
			
			isCompressing = false;
			for (double z = 0.475; z >= 0.400; z = z - 0.00001)
				agilent.writeCommand("voltage " + z + " VPP");
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} // end try-catch
		
		setSweeping(false);
		
	} // end amplitudeSweep
	
	/**
	 * Sweeps the amplitude from the minimum voltage to the maxiumum voltage. Note: min and max are stated in Volts.
	 * 
	 * @param min - The minimum voltage, in Volts.
	 * @param max - The maximum voltage, in Volts.
	 */
	public void amplitudeSweep(double min, double max) {
		
		if (agilent == null)
			System.out.println("I done goofed.");
		
		try {
			
			for (double z = min; z <= max; z = z + 0.00001)
				agilent.writeCommand("voltage " + z + " VPP");
			
			for (double z = max; z >= min; z = z - 0.00001)
				agilent.writeCommand("voltage " + z + " VPP");
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} // end try-catch
		
		setSweeping(false);
		
	} // end amplitudeSweep
	
	/**
	 * Commands the function generator to sweep from the specified start value to the specified end value or vice-versa.
	 * 
	 * @param start - The starting voltage value. Must be less than @param end.
	 * @param end - The ending voltage value. Must be greater than @param start.
	 * @param reverse - Boolean flag to state whether or not to run the sweep from end to start.
	 */
	public void sweepTo(double start, double end, boolean reverse) {
		
		if (!reverse) {
			
			try {
				
				for (double z = start; z <= end; z = z + 0.00001)
					agilent.writeCommand("voltage " + z + " VPP");
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} // end try-catch
			
		} else {
			
			try {
				
				for (double z = end; z >= start; z = z - 0.00001)
					agilent.writeCommand("voltage " + z + " VPP");
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} // end try-catch
			
		} // end if-else
		
	} // end sweepTo
	
	/**
	 * Method to check if the function generator is currently being commanded to sweep amplitudes - returns true if a sweep is in progress.
	 * 
	 * @return sweepInProgress - Boolean value that tracks if a sweep is currently in progress.
	 */
	public boolean isSweeping() {
		return sweepInProgress;
	} // end isSweeping
	
	/**
	 * Sets the value of sweepInProgress to either true or false.
	 * 
	 * @param value - Set to true if starting a sweep and to false if finishing a sweep.
	 */
	public void setSweeping(boolean value) {
		sweepInProgress = value;
	} // end setSweeping
	
	/**
	 * Commands the function generator to set the amplitude to the desired voltage.
	 * 
	 * @param voltage - Desired amplitude voltage, in Volts. Note: Function generator should only be commanded between 600 - 750 mV if you are not engaging modulation mode.
	 */
	public void setAmplitude(double voltage) {
		
		if (agilent != null) {

			try {
				agilent.writeCommand("voltage " + voltage + " VPP");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} // end try-catch

		} // end if
		
	} // end setAmplitude
	
	/**
	 * Commands the function generator to set the freqency.
	 * 
	 * @param freq - The desired frequency in Hz.
	 */
	public void setFrequecy(double freq) {
		
		if (agilent != null) {

			try {
				agilent.writeCommand("frequency " + freq);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} // end try-catch

		} // end if
		
	} // end setFrequency
	
	/**
	 * Commands the function generator... TO BEEP!
	 */
	public void beep() {
		
		if (agilent != null) {

			try {
				agilent.writeCommand("system:beep");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} // end try-catch

		} // end if
		
	} // end beep
	
	/**
	 *  Asks the function generator for the current frequency. Returns zero if something is amiss.
	 *  
	 * @return The funcion generator's frequency in Hz.
	 */
	public double getFrequency() {
		
		if (agilent != null) {

			try {
				return Double.parseDouble(agilent.sendCommand("frequency?"));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} // end try-catch

		} // end if
		
		return 0;
		
	} // end getFrequency
	
	/**
	 *  Asks the function generator for the current amplitude. Returns zero if something is amiss.
	 *  
	 * @return The funcion generator's amplitude in Volts.
	 */
	public double getAmplitude() {
		
		if (agilent != null) {

			try {
				return Double.parseDouble(agilent.sendCommand("voltage?"));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} // end try-catch

		} // end if
		
		return 0;
		
	} // end getFrequency
	
	/**
	 * Checks to see if we are currently compressing or relaxing the droplet. Returns true if we are compressing.
	 * 
	 * @return True if compressing, false if relaxing.
	 */
	public boolean isCompressing() {
		return isCompressing;
	} // end isCompressing
	
} // end AgilentFunctionGenerator
