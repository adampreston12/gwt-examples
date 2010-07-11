package org.gonevertical.core.server.db.oauth;


import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.gonevertical.core.client.oauth.OAuthTokenData;
import org.gonevertical.core.client.ui.login.UserData;
import org.gonevertical.core.server.ServerPersistence;
import org.gonevertical.core.server.jdo.data.SessionAccessTokenJdo;
import org.gonevertical.core.server.jdo.data.SessionNonceJdo;
import org.gonevertical.core.server.jdo.data.ThingJdo;
import org.gonevertical.core.server.jdo.data.ThingTypeJdo;

public class OAuthServer {
	
	private static final Logger log = Logger.getLogger(OAuthServer.class.getName());

  private ServerPersistence sp = null;

	private ThingJdo tj;

	/**
	 * constructor
	 * 
	 * @param sp
	 */
  public OAuthServer(ServerPersistence sp) {
    this.sp = sp;
    tj = new ThingJdo(sp);
  }
  
  /**
   * use this to debug
   * 
   * @param debug
   */
  @Deprecated
  private void debug(String debug) {
    System.out.println(debug);
    sp.getLogger().warning(debug);
  }
  
  /**
   * A. -> (B. grant request token): Can we go to the next step? Service
   * Provider (this), can grant request token?
   * 
   * NOTE: using rpc, I am only using the app base url, to sign, and no port
   * 
   * @param token
   * @return
   */
  public OAuthTokenData requestToken(OAuthTokenData token) {

    String url = sp.getRequestUrlOAuth();
    
    sp.getLogger().info("requestToken:  url: " + url);

    // get the application data according to the consumerKey Given, then lets
    // see if it matches up
    ApplicationData appData = getApplicationId(token);

    if (appData == null) {
    	sp.getLogger().severe("OAuthServer.requestToken(): Error: No applications are set yet.");
    	return null;
    }
    
    // verify the signed signature from the client matches the local
    boolean doesSignatureMatch = token.verify(url, appData.consumerSecret);

    // check nonce - makeure it does not exist
    boolean nonceAlreadyUsed = hasNonceBeenFoundUsed(token, (long) ThingTypeJdo.TYPE_APPLICATION, appData.applicationId);

    // prepare for transport back
    OAuthTokenData returnToken = new OAuthTokenData();

    // examine if we can go to the next step
    if (appData.applicationId == 0 || doesSignatureMatch == false || nonceAlreadyUsed == true) {
      returnToken.setResult(OAuthTokenData.ERROR);
    } else {
      returnToken.setResult(OAuthTokenData.SUCCESS);
    }

    // on application credentials success - grant request token access
    AccessTokenData at = new AccessTokenData();
    if (returnToken.getResult() == OAuthTokenData.SUCCESS) {
      at = setAccessToken_application(appData.applicationId);
      returnToken.setAccessToken(at.accessToken, at.accessTokenSecret);
    }

    // set nonce, so it can't be used again
    setNonce(token, url, (long) ThingTypeJdo.TYPE_APPLICATION, appData.applicationId);

    sp.getLogger().info("requestToken: url: " + url + " secret: " + at.accessTokenSecret);

    try {
      returnToken.sign(url, at.accessTokenSecret);
    } catch (Exception e) {
    	sp.getLogger().log(Level.SEVERE, "requestToken: ****** ERROR SIGNING ******", e);
      e.printStackTrace();
    }

    // transport back to client
    return returnToken;
  }

  /**
   * get user Access Token
   * 
   * TODO - I am going to have to come back and rewrite this later. This isn't
   * correct yet. Need more awakeness. :)
   * 
   * 
   * @param appAccessToken  - apps access token with users key and secret transfer app access
   *          to user access
   * @param url
   * @return
   */
  public OAuthTokenData getUserAccessToken(OAuthTokenData appAccessToken) {

    String url = sp.getRequestUrlOAuth();
    
    // debug
    System.out.println("\n***************\n***************\nOAuthServer.getUserAccessToken(): Token Output: OAuthTokenData=" + appAccessToken.toString());

    // find the user? - return null if none
    UserData userData = getUserData(appAccessToken);
    if(userData == null) {
      appAccessToken.setResult(OAuthTokenData.ERROR_USERNOTFOUND);
      System.out.println("OAuthServer.getUserAccessToken(): Warning: Sorry, I could not find the user for comparison. Returning for try agian.");
      return appAccessToken;
    }
    
    System.out.println("OAuthServer.getUserAccessToken(): Incoming url=" + url + " Found User key=" + userData.getConsumreKey() + " secret=" + userData.getConsumerSecret());
    
    // verify the signed signature from the client matches the local
    boolean verifySignature = appAccessToken.verify(url, userData.getConsumerSecret());

    System.out.println("OAuthServer.getUserAccessToken(): Signatures Pass: [true|false] " + verifySignature);
    
    // if true - thats bad
    boolean nonceAlreadyUsed = hasNonceBeenFoundUsed(appAccessToken, (long) ThingTypeJdo.TYPE_USER, userData.getUserId());

    // verify application's access, then transfer it to the user if all passes
    long accessId = getAccessId(appAccessToken);

    System.out.println("OAuthServer.getUserAccessToken(): Retrieved AccessId=" + accessId);
    
    // examine if we can go to the next step
    if (userData != null && 
        accessId == 0 | 
        userData.getUserId() == 0 | 
        userData.getErrorInt() > 0 | 
        verifySignature == false | nonceAlreadyUsed == true) {
      // TODO - make the error more granular
      appAccessToken.setResult(OAuthTokenData.ERROR);
    } else {
      appAccessToken.setResult(OAuthTokenData.SUCCESS);
    }

    // change access token session to user - after app credentials pass
    if (appAccessToken.getResult() == OAuthTokenData.SUCCESS) {
      setAccessToken_user(accessId, userData.getUserId());
    } else {
      // TODO - what to do, what to do?
    }

    // set nonce, so it can't be used again.
    setNonce(appAccessToken, url, (long) ThingTypeJdo.TYPE_USER, userData.getUserId());

    // be sure not to send back the userkey, b/c it transforms into token
    appAccessToken.setConsumerKey(null);
    
    // sign the token for transport back
    try {
      appAccessToken.sign(url, userData.getConsumerSecret());
    } catch (Exception e) {
    	sp.getLogger().log(Level.SEVERE, "requestToken: ****** ERROR SIGNING", e);
      e.printStackTrace();
    }

    // if we make it this far, it becomes userAccessToken
    // this is just for looks
    OAuthTokenData userAccessToken = appAccessToken;

    // transport back to client
    return userAccessToken;
  }

  /**
   * find the user from consumerKey and consumerSecret also finds if the user
   * actually exists will use it to verify the signing
   * 
   * @param token
   * @return
   */
  private UserData getUserData(OAuthTokenData token) {

    String consumerKey = token.getConsumerKey();

    // get user (thing)
    ThingJdo[] users = tj.query((long)ThingTypeJdo.TYPE_USER, consumerKey);
    
    UserData ud = null;
    if (users == null || token == null || users == null || users.length == 0) {
      sp.getLogger().info("OAuthServer.getUserData(): Warning: Sorry, I couldn't find the user with that key=" + consumerKey + ". Will return to try agian.");
    } else {
      ud = new UserData();
      ud.setUserId(users[0].getThingId());
      ud.setConsumerKey(users[0].getKey());
      ud.setConsumerSecret(users[0].getSecret());
    }

    return ud;
  }

  /**
   * get the applications id - from app consumer key
   * 
   * @param token
   * @return
   */
  private ApplicationData getApplicationId(OAuthTokenData token) {

    if (token == null) {
      System.out.println("No tokenData exists. ERROR: getApplicationId()");
    }

    String ck = token.getConsumerKey();
    
    // get application (thing)
    ThingJdo[] things = tj.query((long) ThingTypeJdo.TYPE_APPLICATION, ck);
    if (things == null || things.length == 0) {
    	System.out.println("no applications set");
    	return null;
    }
    long id = things[0].getThingId();
    String consumerKey = things[0].getKey();
    String consumerSecret = things[0].getSecret();
    
    ApplicationData r = new ApplicationData();
    r.applicationId = id;
    r.consumerKey = consumerKey;
    r.consumerSecret = consumerSecret;
      
    return r;
  }
  
  @Deprecated
  public UserData getThingId(OAuthTokenData accessToken) {

    if (accessToken == null) {
      System.out.println("No tokenData exists. ERROR: User.getUserId()");
    }

    String getConsumerKeyUser = accessToken.getConsumerKey();
    
    // get user (thing)
    ThingJdo[] things = tj.query((long) ThingTypeJdo.TYPE_USER, getConsumerKeyUser);
    long id = things[0].getThingId();
    String consumerKey = things[0].getKey();
    String consumerSecret = things[0].getSecret();
    
    UserData r = new UserData();
    r.setUserId(id);
    r.setConsumerKey(consumerKey);
    r.setConsumerSecret(consumerSecret);
      
    return r;
  }
  
  /**
   * verify this nonce has not been used before nonce - 30 chars, and is unique
   * for each request
   * 
   * @param token - token sent from client
   * @param thingTypeid 
   * @param thingId 
   * @return
   */
  private boolean hasNonceBeenFoundUsed(OAuthTokenData token, long thingTypeId, long thingId) {
    if (token == null | thingTypeId == 0 | thingId == 0) {
      return false;
    }
    String whereQuery = "(ThingTypeId='" + thingTypeId + "') AND (ThingId='" + thingId + "')";
   
    if (whereQuery.length() == 0) {
      System.out.println("ERROR: verifyNonce(), for some reason there is no appId or userId");
      return false;
    }
    
    String nonce = token.getNonce();

    SessionNonceJdo snj = new SessionNonceJdo(sp);
    boolean found = snj.doesNonceExist(thingTypeId, thingId, nonce);
 
    return found;
  }

  /**
   * record nonce that has been used
   * 
   * @param token
   * @param url
   * @param thingTypeId
   * @param thingId
   */
  private void setNonce(OAuthTokenData token, String url, Long thingTypeId, Long thingId) {
    SessionNonceJdo n = new SessionNonceJdo(sp);
    n.insert(url, thingTypeId, thingId, token.getNonce());
  }

  /**
   * set a unique access token for the session for the application
   * 
   * @param applicationId
   * @return
   */
  private AccessTokenData setAccessToken_application(Long applicationId) {

    String accessKey = getAccessKey();
    String accessSecret = getAccessSecret();

    SessionAccessTokenJdo sa = new SessionAccessTokenJdo(sp);
    boolean success = sa.insert((long)ThingTypeJdo.TYPE_APPLICATION, applicationId, accessKey, accessSecret);
    
    AccessTokenData at = new AccessTokenData();
    if (success == true) {
      at.accessToken = accessKey;
      at.accessTokenSecret = accessSecret;
    } else {
      at = null;
    }
    
    return at;
  }

  /**
   * get the AccessId - verify there is a session token
   * 
   * @param appAccessToken
   * @return
   */
  private long getAccessId(OAuthTokenData appAccessToken) {

    String appConsumerKey = appAccessToken.getAccessToken_key();
    String appConsumerSecret = appAccessToken.getAccessToken_secret();

    SessionAccessTokenJdo satj = new SessionAccessTokenJdo(sp);
    SessionAccessTokenJdo[] sas = satj.query(appConsumerKey, appConsumerSecret);
    SessionAccessTokenJdo sa = null;
    if (sas != null) {
      sa = sas[0];
    }

    long accessId = sa.getId();

    return accessId;
  }

  /**
   * set the access token for the session, but then then transfer it to this
   * user here
   * 
   * @param id - the unique data object id for that token
   * @param userId
   * @return
   */
  private boolean setAccessToken_user(Long id, Long userId) {
  	SessionAccessTokenJdo satj = new SessionAccessTokenJdo(sp);
    return satj.updateAccessToken(id, userId);
  }

  /**
   * get accessToken (key) Creates a unique AccessKey for the session
   * 
   * The goal here is to have a unique Key and Secret for the Session which the
   * App or User can own for the session. Everything builds up to this.
   * 
   * @return
   */
  private String getAccessKey() {
    UUID uId = UUID.randomUUID();
    return uId.toString();
  }

  /**
   * get accesstoken (secret) Creates a unique Secret for teh session
   * 
   * The goal here is to have a unique Key and Secret for the Session which the
   * App or User can own for the session. Everything builds up to this.
   * 
   * @return
   */
  private String getAccessSecret() {

    int nounceLength = 15;
    String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXTZabcdefghiklmnopqrstuvwxyz";
    String s = "";
    for (int i = 0; i < nounceLength; i++) {
      int rnum = (int) Math.floor(Math.random() * chars.length());
      s += chars.substring(rnum, rnum + 1);
    }
    return s;

  }


}
