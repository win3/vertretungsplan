/*  LS Vertretungsplan - Android-App f�r den Vertretungsplan der Lornsenschule Schleswig
    Copyright (C) 2014  Johan v. Forstner

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see [http://www.gnu.org/licenses/]. */

package com.johan.vertretungsplan.background;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.holoeverywhere.app.Activity;
import org.holoeverywhere.preference.PreferenceManager;
import org.holoeverywhere.preference.SharedPreferences;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import com.google.analytics.tracking.android.EasyTracker;
import com.google.analytics.tracking.android.Tracker;
import com.google.gson.Gson;
import com.joejernst.http.Request;
import com.joejernst.http.Response;
import com.johan.vertretungsplan.classes.Schule;
import com.johan.vertretungsplan.classes.Vertretungsplan;
import com.johan.vertretungsplan.parser.BaseParser;
import com.johan.vertretungsplan.parser.UntisMonitorParser;
import com.johan.vertretungsplan.utils.Utils;
import com.johan.vertretungsplan_2.R;
import com.johan.vertretungsplan_2.StartActivity;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.widget.RemoteViews;

public class VertretungsplanService extends IntentService {
	public static String UPDATE_ACTION = "UPDATE";
	static SharedPreferences settings;
	static Bundle extras;
	static Context context;

	public VertretungsplanService() {
		super("VertretungsplanService");
	}

	// Will be called asynchronously by Android
	@Override
	protected void onHandleIntent(Intent intent) {
		context = this;
		settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		extras = intent.getExtras();
		Gson gson = new Gson();

		analytics();

		boolean autoSync;
		try {
			autoSync = extras.getBoolean("AutoSync");	
		} catch (NullPointerException e) {
			autoSync = false;
		}

		ConnectivityManager conMan = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

		//wifi
		State wifi = conMan.getNetworkInfo(1).getState();

		if (wifi == NetworkInfo.State.CONNECTED || autoSync == false || settings.getBoolean("syncWifi", false) == false) {

			Log.d("Vertretungsplan", "WiFi state: " + wifi);
			Log.d("Vertretungsplan", "autoSync: " + autoSync);
			Log.d("Vertretungsplan", "syncWifi: " + Boolean.valueOf(settings.getBoolean("syncWifi", false)));

			Log.d("Vertretungsplan", "Vertretungsplan wird abgerufen");
			
			try {

				List<Schule> schools = Utils.getSchools(this);
				String id = settings.getString("selected_school", "");
				Schule schule = null;
				int i = 0;
				while (i < schools.size() && schule == null) {
					if(schools.get(i).getId().equals(id))
						schule = schools.get(i);
					i++;
				}
				
				BaseParser parser = null;
				if (schule.getApi().equals("untis-monitor")) {
					parser = new UntisMonitorParser();
				} //else if ... (andere Parser)
			
				//Vertretungsplan-Objekt erzeugen
				Vertretungsplan v = parser.parseVertretungsplan(schule);
				settings.edit().putString("Vertretungsplan", gson.toJson(v)).commit();
	
				// Sucessful finished
				int result = Activity.RESULT_OK;	
	
				if(extras != null) {
					if(extras.get("MESSENGER") != null) {
						nachrichtAnApp(extras, result, v);
					}
				}
				widgetAktualisieren();
	
	//			String standWinter = null;
	//			Vertretungsplan vAlt = null;
	//
	//			if (settings.getBoolean("notification", true) && !settings.getBoolean("isInForeground", false)) {
	//				//Benachrichtigung anzeigen
	//
	//				if (settings.getString("Vertretungsplan", null) != null) {
	//					vAlt = gson.fromJson(settings.getString("Vertretungsplan", ""), Vertretungsplan.class);		        						        
	//					try {
	//						//Alten Winterausfall-Stand lesen
	//						standWinter = vAlt.getWinterAusfall().getStand();
	//					} catch (NullPointerException e) {}
	//				}
	//				//Wenn sich etwas ge�ndert hat, Benachrichtigungen geben
	//				String standWinterNeu = docWinter.select("pubDate").first().text();
	//
	//				if (standWinter != null && standWinterNeu != null && !(standWinterNeu.equals(standWinter))) {
	//					//Es gibt �nderungen beim Winter-Schulausfall
	//					String text = docWinter.select("item description").first().text(); 
	//					if (!text.contains("Aktuell gibt es keine Hinweise auf witterungsbedingten Unterrichtsausfall.")) {
	//						benachrichtigung(-1); //-1 steht f�r Winter-Schulausfall
	//					}
	//
	//				} else {
	//					String klasse = settings.getString("klasse", null);
	//					if(klasse != null) {
	//						//Pr�fen, ob �nderungen schon gestern bekannt waren und es keine �nderungen f�r Morgen gibt
	//						boolean schonGesternBekannt = false;
	//						boolean garNichtsVeraendert = false;
	//						if (vAlt != null && v !=  null) {
	//
	//							if (vAlt.get(klasse) != null) {
	//								if (v.get(klasse) != null) {
	//									//Bei alt und neu sind �nderungen
	//									schonGesternBekannt = v.get(klasse).getVertretungHeute().equals(vAlt.get(klasse).getVertretungMorgen()) && v.get(klasse).getVertretungMorgen().size() == 0;
	//									garNichtsVeraendert = v.get(klasse).equals(vAlt.get(klasse));
	//								} else {
	//									//�nderungen nur bei alt
	//								}
	//							} else {
	//								if (v.get(klasse) != null) {
	//									//�nderungen nur bei neu							    					
	//								} else {
	//									//Keine �nderungen bei beiden
	//									garNichtsVeraendert = true; //Keine �nderungen vorher und nachher
	//								}
	//							}
	//
	//						}
	//						Log.d("Vertretungsplan", "garnichts: " + garNichtsVeraendert);
	//						Log.d("Vertretungsplan", "schonGesternBekannt: " + schonGesternBekannt);
	//						if (!garNichtsVeraendert && !schonGesternBekannt) {
	//							int anzahlAenderungen = 0;
	//							if(v.get(klasse) != null) {
	//								if (v.get(klasse).getVertretungHeute() != null)
	//									anzahlAenderungen += v.get(klasse).getVertretungHeute().size();
	//								if (v.get(klasse).getVertretungMorgen() != null)
	//									anzahlAenderungen += v.get(klasse).getVertretungMorgen().size();
	//							}
	//							if (anzahlAenderungen != 0) {
	//								benachrichtigung(anzahlAenderungen);
	//							}
	//						}
	//					}
	//				}		
	//			}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void changelogPruefen(Document docChangelog) {

		PackageInfo pInfo = null;
		try {
			pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
		} catch (NameNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String version = pInfo.versionName;
		try {
			Element changelog = docChangelog.select("release").last();
			if (!changelog.attr("version").equals(version)) {
				String newVersion = changelog.attr("version");
				String changelogText = changelog.text();

				String sound = settings.getString("ringtone", RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString());

				NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
				.setSmallIcon(R.drawable.ic_notification)
				.setContentTitle("Vertretungsplan App Update")
				.setContentText("Version " + newVersion)
				.setStyle(new NotificationCompat.BigTextStyle()
				.bigText("Version " + newVersion + "\n" + changelogText));
				if (!sound.equals("")) {
					Uri soundUri = Uri.parse(sound);
					mBuilder.setSound(soundUri);
				}
				mBuilder.setDefaults(Notification.DEFAULT_VIBRATE|Notification.DEFAULT_LIGHTS);
				mBuilder.setOnlyAlertOnce(true);
				mBuilder.setAutoCancel(true);
				Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://dl.dropboxusercontent.com/u/34455869/VertretungsplanApp%20Download/Vertretungsplan.apk"));
				TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
				stackBuilder.addParentStack(StartActivity.class);
				stackBuilder.addNextIntent(browserIntent);
				PendingIntent resultPendingIntent =
						stackBuilder.getPendingIntent(
								0,
								PendingIntent.FLAG_UPDATE_CURRENT
								);
				mBuilder.setContentIntent(resultPendingIntent);
				NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
				// mId allows you to update the notification later on.
				int mId = 2;
				mNotificationManager.notify(mId, mBuilder.build());

			}
		} catch (NullPointerException e) {
			Log.d("Vertretungsplan", "Serverproblem beim Changelog");
		}

	}

	private void widgetAktualisieren() {    
		//Widget(s) aktualisieren
		Intent i = new Intent(context, VertretungsplanWidgetProvider.class); 
		i.setAction(VertretungsplanWidgetProvider.UPDATE_ACTION); 
		context.sendBroadcast(i); 
	}

	private void nachrichtAnApp(Bundle extras, int result, Vertretungsplan v) {
		//Nachricht senden an App
		Log.d("Vertretungsplan", "heruntergeladen, sende Nachricht an App");
		if (extras != null) {
			Messenger messenger = (Messenger) extras.get("MESSENGER");
			Message msg = Message.obtain();

			try {
				RemoteViews rv = (RemoteViews) extras.get("RV");
				int widgetId = ((Integer) extras.get("WID")).intValue();
				List<Object> list = new ArrayList<Object>();
				list.add(rv);
				list.add(widgetId);
				msg.obj = list;
			} catch(Throwable e) {

			}

			msg.arg1 = result;
			Bundle bundle = new Bundle();
			bundle.putSerializable("Vertretungsplan", v);		  	      
			msg.setData(bundle);
			try {
				messenger.send(msg);
			} catch (Throwable e1) {
				Log.w(getClass().getName(), "Exception sending message", e1);
			}

		}
	}

	private static void benachrichtigung(Integer anzahlAenderungen) {
		if (settings.getBoolean("notification", true) == true) {
			//Benachrichtigung anzeigen
			if(settings.getBoolean("isInForeground", false)){
				//App wird angezeigt
			} else {
				//App wird nicht angezeigt
				String sound = settings.getString("ringtone", RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString());

				NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
				.setSmallIcon(R.drawable.ic_notification)
				.setContentTitle("Vertretungsplan");
				if (anzahlAenderungen == null) {
				} else {
					mBuilder.setNumber(anzahlAenderungen.intValue());
				}
				if (anzahlAenderungen == -1) {
					//Winter-Schulausfall
					mBuilder.setContentText("Es gibt �nderungen beim Winter-Schulausfall!");
				} else {
					mBuilder.setContentText("Es gibt �nderungen auf dem Vertretungsplan!");
				}
				if (!sound.equals("")) {
					Uri soundUri = Uri.parse(sound);
					mBuilder.setSound(soundUri);
				}
				mBuilder.setDefaults(Notification.DEFAULT_VIBRATE|Notification.DEFAULT_LIGHTS);
				mBuilder.setOnlyAlertOnce(true);
				mBuilder.setAutoCancel(true);
				// Creates an explicit intent for an Activity in your app
				Intent resultIntent = new Intent(context, StartActivity.class);

				// The stack builder object will contain an artificial back stack for the
				// started Activity.
				// This ensures that navigating backward from the Activity leads out of
				// your application to the Home screen.
				TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
				// Adds the back stack for the Intent (but not the Intent itself)
				stackBuilder.addParentStack(StartActivity.class);
				// Adds the Intent that starts the Activity to the top of the stack
				stackBuilder.addNextIntent(resultIntent);
				PendingIntent resultPendingIntent =
						stackBuilder.getPendingIntent(
								0,
								PendingIntent.FLAG_UPDATE_CURRENT
								);
				mBuilder.setContentIntent(resultPendingIntent);
				NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
				// mId allows you to update the notification later on.
				int mId = 1;
				mNotificationManager.notify(mId, mBuilder.build());
			}
		}
	}

	private void analytics() {
		EasyTracker.getInstance().setContext(this);
		Tracker tracker = EasyTracker.getTracker();

		String klasse = settings.getString("klasse", "unbekannt");
		Boolean sync = settings.getBoolean("sync", true);
		String syncPeriod;
		String benachrichtigung;
		if (sync == true) {
			syncPeriod = settings.getString("syncPeriod", "unbekannt");
			benachrichtigung = Boolean.valueOf(settings.getBoolean("notification", true)).toString();
		} else {
			syncPeriod = "Sync ausgeschaltet";
			benachrichtigung = "Sync ausgeschaltet";
		}
		tracker.setCustomDimension(1, klasse);
		tracker.setCustomDimension(2, syncPeriod);
		tracker.setCustomDimension(3, benachrichtigung);
	}

}
