package simulator;

import gov.nasa.jpf.vm.Verify;

import java.util.*;
import java.util.Map.Entry;

public class Simulator {

	public enum DebugMode {
		DEBUG,
		PROD
	}
	
	public enum DurationMode {
		MIN,
		MAX,
		MEAN,
		MIN_MAX,
		MIN_MAX_MEAN
	}
	
	private ITeam _team;
	private IDeltaClock _clock;
	private HashMap<IActor, ITransition> _ready_transitions = new HashMap<IActor, ITransition>();
	private HashMap<IActor, Integer> _ready_durations = new HashMap<IActor, Integer>();
	private ArrayList<IActor> _active_events = new ArrayList<IActor>();
	private DebugMode _debugMode;
	private DurationMode _duration;
	private Random _random;
	private String _path;

	//Singleton variables
	private boolean _setup = false;
	private static Simulator _instance = null;
	private Date _date = null;

	/**
	 * Get simulator singleton
	 * @return
	 */
	public static synchronized Simulator getSim() {
        if (_instance == null) {
            _instance = new Simulator();
        }
        return _instance;
	}

	private Simulator() {
		_clock = new DeltaClock();
		_date = new Date();
		_path = "";
	}

	public void setup(ITeam team, DebugMode mode, DurationMode duration)
	{
		_setup = false;
		_clock = new DeltaClock();

		_team = team;
		_debugMode = mode;
		_duration = duration;

		_random = new Random();
		_random.setSeed(0);
		_setup = true;
	}

	/**
	 * Main Simulation method.
	 * @return 
	 */
	public String run()
	{
		assert _setup : "Simulator not setup correctly";

		do {
//			if(_clock.getElapsedTime() >= 205)
//				System.out.print("");
			updateTransitions();

			getEnabledTransitions();
			_clock.advanceTime();
			clearTeamChannels();
			processReadyTransitions();
			//printTeamChannels();
		} while (!_ready_transitions.isEmpty());
		MetricManager.getInstance().endSimulation(_path);
		return null;
	}

	private void clearTeamChannels() {
		for(Entry<String, ComChannel<?>> channel_entry: this._team.getAllChannels().entrySet()){
			String channel = channel_entry.getKey();
			if(channel.substring(5, 7).equals(channel.substring(8, 10)))
				channel_entry.getValue().set(null);
		}
	}

	private void printTeamChannels() {
		for(Entry<String, ComChannel<?>> channel_entry: this._team.getAllChannels().entrySet()){
			String channel = channel_entry.getKey();
			System.out.println(channel.substring(5, 7) + " " + channel.substring(8, 10));
			if(channel.substring(5, 7).equals(channel.substring(8, 10)))
				System.out.println(channel_entry.getKey() + ": " + channel_entry.getValue());
		}

	}

	//
	//	HELPER METHODS
	//

	private void updateTransitions() {
		_team.updateTransitions();
	}

	private void getEnabledTransitions()
	{
		//Get transitions from the events
		for(IEvent e : _team.getEvents() ) {
			
			IActor a = (IActor) e;
			ITransition t = e.getEnabledTransition();
			
			if ( _clock.getActorTransition(a) == null ) {
				if ( t != null && !e.isFinished() ) {
					int duration = getDuration(t.getDurationRange());
					
					_clock.addTransition(a, t, duration);
					_ready_durations.put(a, duration);
					
					e.decrementCount();
				}
			} else {
				if ( t == null ) {
					_clock.removeTransition((IActor) e);
				}
			}
		}

		//Get transitions from the actors
		HashMap<IActor, ITransition> transitions = _team.getEnabledTransitions();
		for(Map.Entry<IActor, ITransition> transitionEntry : transitions.entrySet() ) {
			IActor actor = transitionEntry.getKey();
			ITransition transition = transitionEntry.getValue();
			
			int numberOfTransitions = actor.getCurrentState().getEnabledTransitions().size();
			int duration = getDuration(transition.getDurationRange());
			
			_clock.addTransition(actor, transition, duration);
			_ready_durations.put(actor, duration);

			//Store enabled transition data
			MetricManager.getInstance().setEnabledTransition(_clock.getElapsedTime(), actor.getName(), actor.getCurrentState().getName(), numberOfTransitions);
		}
		
		for (IActor actor : _team.getActors()){

			//Store active input data
			for(ComChannel<?> input : actor.getCurrentState().getActiveInputs()) {
				MetricManager.getInstance().setActiveInput(_clock.getElapsedTime(), actor.getName(), actor.getCurrentState().getName(), input.getValue().toString());
			}
			//Store active output data
			for(ComChannel<?> output : actor.getCurrentState().getActiveOutputs()) {
				MetricManager.getInstance().setActiveOutput(_clock.getElapsedTime(), actor.getName(), actor.getCurrentState().getName(), output.getValue().toString());
			}
		}

		//Deactivate outputs from events after one cycle
		for(IEvent e : _team.getEvents() ) {
			ITransition t = e.getEnabledTransition();
			if(t == null){
				e.deactivate();
			}
		}
	}

	private void processReadyTransitions() {
		_ready_transitions.clear();
		_ready_transitions.putAll(_clock.getReadyTransitions());
		for(Entry<IActor, ITransition> readyTransition : _ready_transitions.entrySet()){
			if(_debugMode == DebugMode.DEBUG)
				System.out.println(_clock.getElapsedTime() + "\t" + readyTransition.toString());
			
			IActor actor = (IActor) readyTransition.getKey();
			ITransition transition = (ITransition) readyTransition.getValue();
			
			transition.fire();
			_path += (_clock.getElapsedTime() + "\t" + readyTransition.toString() + "\n");
			int duration = _ready_durations.get(actor);
			
			//Store transition duration data
			MetricManager.getInstance().setTransitionDuration(_clock.getElapsedTime(), actor.getName(), actor.getCurrentState().getName(), duration);
		}
	}

	//
	//	public access
	//

	public int getDuration(Range range)
	{
		int max = range.max();
		int min = range.min();
		int mean = range.mean();
		switch(_duration) {
			case MIN:
				return min;
			case MAX:
				return max;
			case MEAN:
				return mean;
			case MIN_MAX:
				return Verify.getIntFromList(min, max);
			case MIN_MAX_MEAN:
				return Verify.getIntFromList(min, max);
			default:
				return 1;
		}
	}
	
	public Integer getClockTime() {
		return _clock.getElapsedTime();
	}
}
