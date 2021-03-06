package com.michingo.robovmbindings.playservices;

import java.util.ArrayList;

import org.robovm.cocoatouch.foundation.NSArray;
import org.robovm.cocoatouch.foundation.NSData;
import org.robovm.cocoatouch.foundation.NSError;
import org.robovm.cocoatouch.foundation.NSObject;
import org.robovm.cocoatouch.foundation.NSString;
import org.robovm.cocoatouch.uikit.UIViewController;

import com.michingo.robovmbindings.gpgs.GPGAchievement;
import com.michingo.robovmbindings.gpgs.GPGAchievementController;
import com.michingo.robovmbindings.gpgs.GPGAchievementControllerDelegate;
import com.michingo.robovmbindings.gpgs.GPGAchievementDidIncrementBlock;
import com.michingo.robovmbindings.gpgs.GPGAchievementDidRevealBlock;
import com.michingo.robovmbindings.gpgs.GPGAchievementDidUnlockBlock;
import com.michingo.robovmbindings.gpgs.GPGAchievementMetadata;
import com.michingo.robovmbindings.gpgs.GPGAchievementModel;
import com.michingo.robovmbindings.gpgs.GPGAchievementState;
import com.michingo.robovmbindings.gpgs.GPGAppStateConflictHandler;
import com.michingo.robovmbindings.gpgs.GPGAppStateLoadResultHandler;
import com.michingo.robovmbindings.gpgs.GPGAppStateModel;
import com.michingo.robovmbindings.gpgs.GPGAppStateWriteResultHandler;
import com.michingo.robovmbindings.gpgs.GPGAppStateWriteStatus;
import com.michingo.robovmbindings.gpgs.GPGLeaderboard;
import com.michingo.robovmbindings.gpgs.GPGLeaderboardController;
import com.michingo.robovmbindings.gpgs.GPGLeaderboardControllerDelegate;
import com.michingo.robovmbindings.gpgs.GPGLeaderboardLoadScoresBlock;
import com.michingo.robovmbindings.gpgs.GPGLeaderboardMetadata;
import com.michingo.robovmbindings.gpgs.GPGLeaderboardModel;
import com.michingo.robovmbindings.gpgs.GPGLeaderboardTimeScope;
import com.michingo.robovmbindings.gpgs.GPGLeaderboardsController;
import com.michingo.robovmbindings.gpgs.GPGLeaderboardsControllerDelegate;
import com.michingo.robovmbindings.gpgs.GPGManager;
import com.michingo.robovmbindings.gpgs.GPGReAuthenticationBlock;
import com.michingo.robovmbindings.gpgs.GPGScore;
import com.michingo.robovmbindings.gpgs.GPGScoreReport;
import com.michingo.robovmbindings.gpgs.GPGScoreReportScoreBlock;
import com.michingo.robovmbindings.gpgs.GPGToastPlacement;
import com.michingo.robovmbindings.gpp.GPPSignIn;
import com.michingo.robovmbindings.gpp.GPPSignInDelegate;
import com.michingo.robovmbindings.gt.GTMOAuth2Authentication;

/** Manager that handles the most common usage of Google Play Game Services. 
 * @author Michael Hadash */
public class PlayServicesManager extends NSObject implements GPPSignInDelegate, GPGAchievementControllerDelegate, GPGLeaderboardControllerDelegate, GPGLeaderboardsControllerDelegate {
	
	public static final int TOAST_WELCOME = 0;
	public static final int TOAST_ACHIEVEMENTS = 1;
	public static final int TOAST_BOTH = 2;
	public static final int DATA_NAME = 0;
	public static final int DATA_AVATAR = 1;
	public static final int DATA_ID = 2;
	
	//identifiers
	private ArrayList<String> ach_ids;
	private ArrayList<String> lead_ids;
	private String clientId;
	
	//view controller
	private UIViewController viewController;
	
	//blocks
	private GPGReAuthenticationBlock gamesAuthBlock;
	private GPGAchievementDidRevealBlock revealBlock;
	private GPGAchievementDidIncrementBlock incrementBlock;
	private GPGAchievementDidUnlockBlock unlockBlock;
	private GPGAppStateWriteResultHandler cloudCompletionHandler;
	private GPGScoreReportScoreBlock postScoreCompletionHandler;
	
	//options
	private boolean fetchName = false;
	private boolean fetchEmail = false;
	private boolean fetchId = true;
	
	/** Call this in your app's didFinishLaunching() method. You must specify your clientID and, if you need user data, what data to load during login before calling this. */
	public void didFinishLaunching(){
		GPPSignIn signIn = GPPSignIn.sharedInstance();
	    signIn.setClientID(clientId);
	    
	    //set scopes
	    ArrayList<NSString> scopes = new ArrayList<NSString>();
		scopes.add(new NSString("https://www.googleapis.com/auth/games"));
		scopes.add(new NSString("https://www.googleapis.com/auth/appstate"));
	    signIn.setScopes(new NSArray<NSString>(scopes));
	    
	    
	    signIn.setDelegate(this);
	    signIn.setShouldFetchGoogleUserID(fetchId);
	    signIn.setShouldFetchGoogleUserEmail(fetchEmail);
	    signIn.setShouldFetchGooglePlusUser(fetchName);
	    
	    //try to sign in silently
	    signIn.trySilentAuthentication();
	    
	    
	    //define blocks
	    revealBlock = new GPGAchievementDidRevealBlock(){
			@Override
			public void invoke(GPGAchievementState state, NSError error) {
				if (error!=null){
					System.out.println("Error while revealing achievement!");
				}
			}
		};
		incrementBlock = new GPGAchievementDidIncrementBlock(){
			@Override
			public void invoke(boolean newlyUnlocked, int currentSteps, NSError error) {
				if (error!=null){
					System.out.println("Error while revealing!");
				}
			}
		};
		unlockBlock = new GPGAchievementDidUnlockBlock(){
			@Override
			public void invoke(boolean newlyUnlocked, NSError error) {
				if (error!=null){
					System.out.println("Error while unlocking!");
				}
			}
		};
		cloudCompletionHandler = new GPGAppStateWriteResultHandler() {
			@Override
			public void invoke(GPGAppStateWriteStatus status, NSError error) {
				switch(status){
					case GPGAppStateWriteStatusSuccess:System.out.println("cloud save succeeded!");break;
					case GPGAppStateWriteStatusBadKeyDataOrVersion:System.out.println("cloud save failed: bad key or version");break;
					case GPGAppStateWriteStatusConflict:System.out.println("cloud save failed: conflict");break;
					case GPGAppStateWriteStatusKeysQuotaExceeded:System.out.println("cloud save failed: keys quota exceeded");break;
					case GPGAppStateWriteStatusNotFound:System.out.println("cloud save failed: not found");break;
					case GPGAppStateWriteStatusSizeExceeded:System.out.println("cloud save failed: size exceeded");break;
					case GPGAppStateWriteStatusUnknownError:System.out.println("cloud save failed: unknown error");break;
				}
			}
		};
		postScoreCompletionHandler = new GPGScoreReportScoreBlock(){
			@Override
			public void invoke(GPGScoreReport report, NSError error) {
				if (error != null){
					System.out.println("score posting failed!");
				}
			}
		};
	}
	
	@Override
	public void finishedWithAuth(GTMOAuth2Authentication auth, NSError error) {
		if (error == null){
			
			//after the google+ sign-in is done, we must continue the sign-in of 'games'.
			startGoogleGamesSignIn();
		}else{
			System.out.println("error during login: "+error.description());
		}
	}
	
	/** Continues the sign-in process. */
	private void startGoogleGamesSignIn() {
		final GPPSignIn s = GPPSignIn.sharedInstance();
		GPGManager m = GPGManager.sharedInstance();
		
		gamesAuthBlock = new GPGReAuthenticationBlock(){
			@Override
			public void invoke(boolean requiresKeychainWipe, NSError error) {
				// If you hit this, auth has failed and you need to authenticate.
	             // Most likely you can refresh behind the scenes
				if (requiresKeychainWipe){
					s.signOut();
				}
				s.authenticate();
			}
		};
		
		//pass the GPPSignIn to the GPGManager.
		m.signIn(s, gamesAuthBlock);
	}
	
	/** Do not use this. This could not be a private member, but do as if it doesn't exists. */
	@Override
	public void achievementViewControllerDidFinish(GPGAchievementController viewController) {
		viewController.dismissViewController(true, null);
	}
	
	/** Do not use this. This could not be a private member, but do as if it doesn't exists. */
	@Override
	public void leaderboardViewControllerDidFinish(GPGLeaderboardController viewController) {
		viewController.dismissViewController(true, null);
	}

	/** Do not use this. This could not be a private member, but do as if it doesn't exists. */
	@Override
	public void leaderboardsViewControllerDidFinish(GPGLeaderboardsController viewController) {
		viewController.dismissViewController(true, null);
	}

	/** Logs you in into Google Play Game Services using the 'games' and 'appstate' scopes. 
	 * Only call this when the user pressed a designated login button. */
	public void login(){
		GPPSignIn.sharedInstance().authenticate();
	}
	
	/** Signs the user out of the services. */
	public void logout(){
		if (isLoggedIn()){
			GPPSignIn.sharedInstance().signOut();
		    GPGManager.sharedInstance().signout();
		}
	}
	
	/** @return whether the user is logged in. */
	public boolean isLoggedIn(){
		if (GPGManager.sharedInstance() == null){
			return false;
		}else{
			return GPGManager.sharedInstance().hasAuthorizer();
		}
	}
	
	/** Call this to pass your achievement identifiers. 
	 * @param achievements an ArrayList containing your identifiers. You can find the identifiers in the Google Play Developers Console. */
	public void provideAchievementIdentifiers(ArrayList<String> achievements){
		this.ach_ids = achievements;
	}
	
	/** Call this to pass your leaderboard identifiers. 
	 * @param achievements an ArrayList containing your identifiers. You can find the identifiers in the Google Play Developers Console. */
	public void provideLeaderboardIdentifiers(ArrayList<String> leaderboards){
		this.lead_ids = leaderboards;
	}
	
	/** Call this to pass your client identifier. 
	 * @param clientId the identifier. You can find the identifier in the Google Play Developers Console. */
	public void setClientId(String clientId){
		this.clientId = clientId;
	}
	
	/** Changes the location where toast messages are displayed. 
	 * @param toastType the type of toast of which you would like to change the location. Choose from the manager's static fields.
	 * @param placement the placement that you want for the toasts. */
	public void setToastLocation(int toastType, GPGToastPlacement placement){
		switch(toastType){
			case TOAST_ACHIEVEMENTS:
				GPGManager.sharedInstance().setAchievementUnlockedToastPlacement(placement);
				break;
				
			case TOAST_WELCOME:
				GPGManager.sharedInstance().setWelcomeBackToastPlacement(placement);
				break;
				
			case TOAST_BOTH:
				GPGManager.sharedInstance().setAchievementUnlockedToastPlacement(placement);
				GPGManager.sharedInstance().setWelcomeBackToastPlacement(placement);
				break;
		}
	}
	
	/** Select the data that you would like to receive from google about the user. 
	 * @param name the full name.
	 * @param email the email address. */
	public void setUserDataToRetrieve(boolean name, boolean email){
		fetchName = name;
		fetchEmail = email;
	}
	
	/** Let the manager know of your root view controller. */
	public void setViewController(UIViewController viewController) {
		this.viewController = viewController;
	}
	
	/** Pops the achievements view controller. This displays all the achievements and the user's progress. */
	public void showAchievements(){
		GPGAchievementController achController = new GPGAchievementController();
		achController.setAchievementDelegate(this);
		viewController.presentViewController(achController, true, null);
	}
	
	/** @return an ArrayList containing all data about your achievements and your user's progress. */
	public ArrayList<GPGAchievementMetadata> getAchievementsList(){
		
		//get the achievement model
		GPGAchievementModel model = GPGManager.sharedInstance().applicationModel().achievement();
		
		//obtain the data and put it in a list
		ArrayList<GPGAchievementMetadata> list = new ArrayList<GPGAchievementMetadata>();
		for(int i=0;i<ach_ids.size();i++){
			list.add(model.metadataForAchievementId(ach_ids.get(i)));
		}
		
		//return the list
		return list;
	}
	
	/** Reveals a hidden achievement. 
	 * @param identifier the achievement identifier. */
	public void revealAchievement(String identifier){
		GPGAchievement a = GPGAchievement.achievementWithId(identifier);
		a.revealAchievementWithCompletionHandler(revealBlock);
	}
	
	/** Reveals a hidden achievement.
	 * @param identifier the achievement identifier.
	 * @param block a block that is invoked when the reveal process ends. Used to check whether it succeeded. Note: make the block an instance member to be sure it is not garbage collected. */
	public void revealAchievement(String identifier, GPGAchievementDidRevealBlock block){
		GPGAchievement a = GPGAchievement.achievementWithId(identifier);
		a.revealAchievementWithCompletionHandler(block);
	}
	
	/** Increments an achievement.
	 * @param identifier the achievement identifier.
	 * @param steps number of steps to increment. */
	public void incrementAchievement(String identifier, int steps){
		GPGAchievement a = GPGAchievement.achievementWithId(identifier);
		a.incrementAchievementNumSteps(steps, incrementBlock);
	}
	
	/** Increments an achievement.
	 * @param identifier the achievement identifier.
	 * @param steps number of steps to increment. 
	 * @param block a block that is invoked when the increment process ends. Used to check whether it succeeded. Note: make the block an instance member to be sure it is not garbage collected. */
	public void incrementAchievement(String identifier, int steps, GPGAchievementDidIncrementBlock block){
		GPGAchievement a = GPGAchievement.achievementWithId(identifier);
		a.incrementAchievementNumSteps(steps, block);
	}
	
	/** Unlocks an achievement. 
	 * @param identifier the achievement identifier. */
	public void unlockAchievement(String identifier){
		GPGAchievement a = GPGAchievement.achievementWithId(identifier);
		a.unlockAchievementWithCompletionHandler(unlockBlock);
	}
	
	/** Unlocks an achievement. 
	 * @param identifier the achievement identifier. 
	 * @param block a block that is invoked when the unlock process ends. Used to check whether it succeeded. Note: make the block an instance member to be sure it is not garbage collected.*/
	public void unlockAchievement(String identifier, GPGAchievementDidUnlockBlock block){
		GPGAchievement a = GPGAchievement.achievementWithId(identifier);
		a.unlockAchievementWithCompletionHandler(block);
	}
	
	/** Posts data (savegame) to google.
	 * @param stateKey your state slot. Can be: 0, 1, 2, 3.
	 * @param data the data that you wish to store. It has a maximum size, which can be found in google's documentation.
	 * @param conflictHandler handler that defines what to do when a conflict occured. It is very important for the user experience to implement this correctly.*/
	public void cloudSave(int stateKey, NSData data, GPGAppStateConflictHandler conflictHandler){
		//get the model
		GPGAppStateModel model = GPGManager.sharedInstance().applicationModel().appState();
		
		//add the data
		model.setStateData(data, stateKey);
		
		//post the data
		model.updateForKey(stateKey, cloudCompletionHandler, conflictHandler);
	}
	
	/** Posts data (savegame) to google.
	 * @param stateKey your state slot. Can be: 0, 1, 2, 3.
	 * @param data the data that you wish to store. It has a maximum size, which can be found in google's documentation.
	 * @param conflictHandler handler that defines what to do when a conflict occured. It is very important for the user experience to implement this correctly.
	 * @param resultHandler handler that is invoked to inform you whether it succeeded or not. */
	public void cloudSave(int stateKey, NSData data, GPGAppStateConflictHandler conflictHandler, GPGAppStateWriteResultHandler resultHandler){
		//get the model
		GPGAppStateModel model = GPGManager.sharedInstance().applicationModel().appState();
		
		//add the data
		model.setStateData(data, stateKey);
		
		//post the data
		model.updateForKey(stateKey, resultHandler, conflictHandler);
	}
	
	/** Loads data (savegame) from google.
	 * @param stateKey your state slot. Can be: 0, 1, 2, 3.
	 * @param conflictHandler handler that defines what to do when a conflict occured. It is very important for the user experience to implement this correctly.
	 * @param resultHandler handler that is invoked to inform you whether it is succeeded or not. If it succeeded, the handler will also contain the data that you requested. */
	public void cloudLoad(int stateKey, GPGAppStateConflictHandler conflictHandler, GPGAppStateLoadResultHandler resultHandler){
		
		//get the model
		final GPGAppStateModel model = GPGManager.sharedInstance().applicationModel().appState();
		
		//start the load request
		model.loadForKey(stateKey, resultHandler, conflictHandler);
	}
	
	/** Retrieves data about the local player. 
	 * @param dataType the data that you want. Choose from DATA_NAME, DATA_AVATAR and DATA_ID. These values are available as static members of PlayServicesManager. */
	public String getUserData(int dataType){
		switch(dataType){
			case DATA_NAME:
				return GPGManager.sharedInstance().applicationModel().player().localPlayer().name();
			case DATA_AVATAR:
				return GPGManager.sharedInstance().applicationModel().player().localPlayer().avatarUrl().toString();
			case DATA_ID:
				return GPGManager.sharedInstance().applicationModel().player().localPlayer().playerId();
		}
		return "";
	}
	
	/** Call to display a specific leaderboard.
	 * @param identifier the leaderboard identifier. */
	public void showLeaderboard(String identifier){
		showLeaderboard(identifier, GPGLeaderboardTimeScope.GPGLeaderboardTimeScopeAllTime);
	}
	
	/** Call to display a specific leaderboard.
	 * @param identifier the leaderboard identifier. 
	 * @param timeScope the default time scope to display. */
	public void showLeaderboard(String identifier, GPGLeaderboardTimeScope timeScope){
		//create the view controller
		GPGLeaderboardController leadController = new GPGLeaderboardController(identifier);
		leadController.setLeaderboardDelegate(this);
		
		//you can choose the default time scope to display in the view controller.
		leadController.setTimeScope(timeScope);
		
		//present the leaderboard view controller
		viewController.presentViewController(leadController, true, null);
	}
	
	/** Displays a list of all leaderboards. The user can then select a leaderboard to display any leaderboard they want from your app. */
	public void showLeaderboardsPicker(){
		//create the view controller
		GPGLeaderboardsController leadsController = new GPGLeaderboardsController();
		leadsController.setLeaderboardsDelegate(this);
		
		//present the leaderboard picker view controller
		viewController.presentViewController(leadsController, true, null);
	}
	
	/** Posts a score to a leaderboard.
	 * @param leaderboardId the identifier of the leaderboard.
	 * @param score the score. */
	public void postScore(String leaderboardId, long score){
		postScore(leaderboardId, score, postScoreCompletionHandler);
	}
	
	/** Posts a score to a leaderboard.
	 * @param leaderboardId the identifier of the leaderboard.
	 * @param score the score. 
	 * @param block a block that is invoked when the posting is completed. 
	 * It contains data about whether it succeeded and whether it is a highscore or not (and in which time scopes).
	 * It will also contain the actual high score if the posted score is not the highscore in the particular time scope. */
	public void postScore(String leaderboardId, long score, GPGScoreReportScoreBlock block){
		//create the score instance
		GPGScore gpgScore = new GPGScore(leaderboardId);
		gpgScore.setValue(score);
		
		//post the score
		gpgScore.submitScoreWithCompletionHandler(block);
	}
	
	/** @return an ArrayList containing all data about your leaderboards. */
	public ArrayList<GPGLeaderboardMetadata> getLeaderboardsList(){
		
		//get the leaderboard model
		GPGLeaderboardModel model = GPGManager.sharedInstance().applicationModel().leaderboard();
		
		//obtain the data and put it in a list
		ArrayList<GPGLeaderboardMetadata> list = new ArrayList<GPGLeaderboardMetadata>();
		for(int i=0;i<lead_ids.size();i++){
			list.add(model.metadataForLeaderboardId(lead_ids.get(i)));
		}
		
		//return the list
		return list;
	}
	
	/** Loads the scores of a leaderboard.
	 * @param leaderboardId the leaderboard identifier.
	 * @param social whether you want social or global scores.
	 * @param timeScope the time scopes where you want the scores for.
	 * @param block the completion handler. This block is invoked when the scores are received. */
	public void getScoresOfLeaderoard(String leaderboardId, boolean social, GPGLeaderboardTimeScope timeScope, GPGLeaderboardLoadScoresBlock block){
		
		//create the leaderboard class
		GPGLeaderboard b = new GPGLeaderboard(leaderboardId);
		
		//set options
		b.setSocial(social);
		b.setTimeScope(timeScope);
		
		//load the scores
		b.loadScoresWithCompletionHandler(block);
	}
}
