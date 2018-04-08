

/**
 * Class dedicated to handling all PID-control related tasks.
 * @author Jarrod Risley
 *
 */

public class PIDSubsystem {

	/*
	 * Local Variables
	 */
	private double kp;
	private double ki;
	private double kd;
	
	private double preError;
	private double integral;
	private double derivative;
	
	private double setpoint; // This setpoint is the droplet's aspect ratio.
	private double startTime;
	private double endTime;
	private double error;
	
	public PIDSubsystem(double kp, double ki, double kd) {
		
		this.kp = kp;
		this.ki = ki;
		this.kd = kd;
		
	} // end constructor
	
	public double calculateOutput(double input) {
		
		endTime = System.currentTimeMillis();
		double deltaT = endTime - startTime;
		
		error = setpoint - input;
		
		if ((Math.abs(setpoint-input))/setpoint < 0.01)
			return 0;
		
		integral = integral + (error * deltaT);
		derivative = (error - preError) / deltaT; 
		
		double output = (kp * error) + (ki * integral) + (kd * derivative);
		
		return output;
	} // end calculateOutput
	
	public boolean setpointReached() {
		
		double percentDifference = (setpoint - Math.abs(error)) / setpoint;
		
		if (percentDifference < 0.1)
			return true;
		else
			return false;
		
	} // end setpointReached
	
	/**
	 * Allows for the constants to be dynamically set from the GUI.
	 * 
	 * @param kp - P Gain
	 * @param ki - I Gain
	 * @param kd - D Gain
	 */
	public void setConstants(double kp, double ki, double kd) {

		this.kp = kp;
		this.ki = ki;
		this.kd = kd;
		
	} // end setConstants
	
	/**
	 * Sets the setpoint for the PID subsystem.
	 * 
	 * @param setpoint The target setpoint for the PID subsystem. In this case, this will be the droplet's aspect ratio.
	 */
	public void setSetpoint(double setpoint) {
		this.setpoint = setpoint;
	} // end setSetpoint
	
	/**
	 * Tells the PID subsystem to set the start time.
	 */
	public void setTime() {
		startTime = System.currentTimeMillis();
	} // end setTime
	
	/**
	 * Grabs the PID constants for display in the GUI. They are contained in an array in this order: kp, ki, kd.
	 * 
	 * @return A double[] containing the PID constants in this order: kp, ki, kd.
	 */
	public double[] getConstants() {
		
		double[] array = {kp, ki, kd};
		return array;
		
	} // end getConstants
	
	
} // end PIDSubsystem
