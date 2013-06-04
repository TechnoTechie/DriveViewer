package com.example.driveviewer;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Changes;
import com.google.api.services.drive.Drive.Files;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.Change;
import com.google.api.services.drive.model.ChangeList;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import android.os.Bundle;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends ListActivity {

	static final int CHOOSE_ACCOUNT = 0;
	static final int REQUEST_ACCOUNT_PICKER = 1;
	static final int REQUEST_AUTHORIZATION = 2;
	static final int DOWNLOAD_FILES = 3;
	static final int SPEED_ANIMATION_TRANSITION = 5;
	static final int INITIAL_LOAD_COUNT = 10;
	static final String REFERENCE = "com.example.driveviewer.DOWNLOAD_URL";
	static final String DRIVE_SERVICE = "com.example.driveviewer.DRIVE_SERVICE";
	static final String FILE_NUMBER = "com.example.driveviewer.FILE_NUMBER";
	public enum Actions { OPEN, RENAME, DELETE };
	public enum AppStatus { LOGIN, LIST, VIEW };
	
	private static Drive service;
	private static GoogleAccountCredential credential;

	private ProgressDialog m_ProgressDialog = null; 
	private ArrayList<FileDisplay> m_files = null;
	private FileManager m_adapter;
	private Runnable viewOrders;
	private TextView debug;
	private ListView myCustomWebViewer;
	private AppStatus state;
	private Date lastAccessDate = new Date(0);
	private Timer syncTimer = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		//webLoaderTest();
		//String htmlTest = "<html><body>Hello page 1</body></html>";
		appInit();
	}
	
	private void webLoaderTest() {
		//ArrayList<WebView> webs = new ArrayList<WebView>();
		myCustomWebViewer = (ListView) findViewById(android.R.id.list);
		WebView mWebView = (WebView) LayoutInflater.from(MainActivity.this).inflate(R.layout.webview, null);;
		
		mWebView.clearView();

		mWebView.setWebViewClient(new WebViewClient());

		WebSettings webSettings = mWebView.getSettings();
		webSettings.setJavaScriptEnabled(true);
		webSettings.setPluginState(WebSettings.PluginState.ON);
		
		mWebView.loadUrl("http://www.google.com");
		//webs.add(mWebView);
		//WebArray test = new WebArray(this, R.layout.row, webs, service);
		myCustomWebViewer.addView(mWebView);
		setContentView(myCustomWebViewer);
	}
	
	@Override
 	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		reDrawTheScreen();
		switch(requestCode){
			case REQUEST_ACCOUNT_PICKER:
				if (resultCode == RESULT_OK && data != null) {
					String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
					if (accountName != null) {
						credential.setSelectedAccountName(accountName);
						service = getDriveService(credential);
						startFileGet();
					}
				} else {
					showToast("Error: Login failed");
					showToast("Please restart the app");
				}
				break;
			case REQUEST_AUTHORIZATION:
				if (resultCode == Activity.RESULT_OK) {
			        startFileGet();
			      } else {
			        startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
			      }
				break;
			default:
				break;
		}
	}
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
	  super.onConfigurationChanged(newConfig);
	  setContentView(R.layout.activity_main);
	}
	/** 
	 * Create a new list and send it to the adapter, tell it to redraw
	 */
	private void reDrawTheScreen(){
		m_files = new ArrayList<FileDisplay>();
		this.m_adapter = new FileManager(this, R.layout.row, m_files, service);
		setListAdapter(this.m_adapter);
		m_adapter.notifyDataSetChanged();
	}
	private void appInit() {
		state = AppStatus.LOGIN;
		m_files = new ArrayList<FileDisplay>();
		this.m_adapter = new FileManager(this, R.layout.row, m_files, service);
		setListAdapter(this.m_adapter);
		debug = (TextView) findViewById(R.id.empty);
		debug.setText("");
		
		myCustomWebViewer = (ListView) findViewById(android.R.id.list);
		
		credential = GoogleAccountCredential.usingOAuth2(this, DriveScopes.DRIVE);
		startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
		
		setupTimer();
	}
	@Override
	public void onResume() {
		super.onResume();
		setupTimer();
	}
	private void setupTimer() {
		if(syncTimer == null) {
			syncTimer = new Timer();
			syncTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					runOnUiThread(new Runnable() {
						public void run() {
							//TODO: //checkForUpdates();
						}
					});
				}
			}, 30000, 30000); }
	}
	private void checkForUpdates(){
		showToast("Checking for updates");
		if(state == AppStatus.VIEW){
			//then do updates for just that file
		} else if(state == AppStatus.LIST) {
			try {
				if(detectChanges()) {
					//if there were changes
					//then notify user: pop-up box is too interrupting
					//  next option is something more subtle
					//  what's more subtle?
					
					//small click-able drop-down box at the top of the page?
					
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			//then check for updates to the entire list
		} else {
			//if the app is here, that means that it's still on login screen
			//do nothing
		}
		//TODO: finish up updates
	}

	@Override
	public void onPause() {
		syncTimer.cancel();
		syncTimer = null;
		super.onPause();
	}
    /**
     * Event Handling for Individual menu item selected
     * Identify single menu item by it's id - Only menu item used in this app is the logout function
     * */
	@Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_logout){
        	reDrawTheScreen();
        	logout();
        	return true;
        } else {
        	return super.onOptionsItemSelected(item);
        }
    }
    /**
     * Logout by refreshing credentials
     */
    private void logout(){
		startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
    }
	private void startFileGet(){
		state = AppStatus.LIST;
		viewOrders = new Runnable(){			
			@Override
			public void run() {				
				try {
					updateData();
					Thread.sleep(10000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}; 
		Thread thread =  new Thread(null, viewOrders, "MagentoBackground");
		thread.start();
		m_ProgressDialog = ProgressDialog.show(MainActivity.this,    
				"Please wait...", "Retrieving data ...", true); 
	}
	private void updateData(){
		ArrayList<FileDisplay> files;
		//getFileList from google and add files to array
		List<File> fileList;
		try {
			files = new ArrayList<FileDisplay>();
			fileList = retrieveFiles("");
			if(fileList != null) { //if returned files not null
				showToast("Number of files in list: " + fileList.size());
				for(File f:fileList){
					//wrap files in file wrapper
					files.add(new FileDisplay(f));
				}
			}
			m_files = files;
		} catch (IOException e) {
			e.printStackTrace();
		}
		runOnUiThread(returnRes);
	}
	private Drive getDriveService(GoogleAccountCredential credential) {
		return new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credential)
		.build();
	}
	public void showToast(final String toast) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(getApplicationContext(), toast, Toast.LENGTH_SHORT).show();
			}
		});
	}
	private Runnable returnRes = new Runnable() {
		@Override
		public void run() {
			if(m_files != null && m_files.size() > 0){
				m_adapter.notifyDataSetChanged();
				for(int i=0;i<m_files.size();i++)
					m_adapter.add(m_files.get(i));
			}
			m_ProgressDialog.dismiss();
			m_adapter.notifyDataSetChanged();
		}
	};
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
	//TODO: File retrieval of folders and searching
	/**
	 * Retrieve a list of File resources. (Google Code)
	 *
	 * @param service Drive API service instance.
	 * @return List of File resources.
	 */
	private List<File> retrieveFiles(String query) throws IOException {
		List<File> result = new ArrayList<File>();
		Files.List request = service.files().list();
		if(query != null) { request.setQ(query); }
		do {
			try {
				showToast("retrieving files");
				FileList files = request.execute();
				
				result.addAll(files.getItems());
				request.setPageToken(files.getNextPageToken());
			}  catch (UserRecoverableAuthIOException e) {
		          startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
		    }  catch (IOException e) {
				showToast("e: " + e);
				System.out.println("An error occurred: " + e);
				request.setPageToken(null);
			}
		} while (request.getPageToken() != null &&
				request.getPageToken().length() > 0);
		return result;
	}
	private boolean detectChanges() throws IOException{
		ArrayList<FileDisplay> files;
		//getFileList from google and add files to array
		List<File> fileList;
		try {
			files = new ArrayList<FileDisplay>();
			fileList = retrieveFiles("");
			if(fileList != null) { //if returned files not null
				for(File f:fileList){
					//wrap files in file wrapper
					if(new Date(f.getModifiedDate().getValue()).compareTo(lastAccessDate) < 0) {
						showToast("Chaaaanges...!");
						return true;
					}
				}
			}
			m_files = files;
		} catch (IOException e) {
			e.printStackTrace();
		}
		showToast("No change...");
		return false;
	}
	//TODO: FileDisplay class
	public class FileDisplay{		
		private String id; //(String, used internally, hide from user)
		private File fileLink; //drive file info
		private String title; //(String, name of file)
		private long fileSize; //size of the file
		private String downloadUrl; //(String, to download file #viewingfiles)
		private DateTime lastViewedByMe; //(DateTime, to allow sorting)
		private boolean viewedYet;
		private String mimeType;
		private Bitmap image;

		public FileDisplay(File file){
			this.fileLink = file;
			this.id = file.getId();
			this.title = file.getTitle();
			this.lastViewedByMe = (file.getLastViewedByMeDate() != null) ? file.getLastViewedByMeDate() : file.getCreatedDate();
			this.viewedYet = file.getLastViewedByMeDate() != null;
			this.fileSize = file.getQuotaBytesUsed().longValue();
			this.mimeType = file.getMimeType();
			this.downloadUrl = file.getAlternateLink();
			try {
				this.image = BitmapFactory.decodeStream((InputStream) new URL(file.getIconLink()).getContent());
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		public String getId() {
			return id;
		}
		public long getFileSize() {
			updateFileSize();
			return fileSize;
		}
		private void updateFileSize(){
			long newSize = fileLink.getQuotaBytesUsed().longValue();
			if(fileSize != newSize) {
				fileSize = newSize;
			}
		}
		public String getTitle() {
			updateTitle();
			return title;
		}
		private void updateTitle(){
			String newTitle = fileLink.getTitle();
			if(title.compareTo(newTitle) != 0) {
				title = newTitle;
			}
		}
		public String getDownloadUrl() {
			//updateDownloadUrl();
			return downloadUrl;
		}
		private void updateDownloadUrl(){
			String newUrl = determineMimeType() ? fileLink.getExportLinks().get("application/pdf") : fileLink.getDownloadUrl();
			if(downloadUrl.compareTo(newUrl) != 0) {
				downloadUrl = newUrl;			
			}
		}
		public DateTime getLastViewedByMe() {
			return lastViewedByMe;
		}
		public void updateLastViewedBy() {
			try {
				service.files().touch(this.id).execute();
			} catch (IOException e) {
				System.out.println("An error occurred: " + e);
			}
		}
		
		public Bitmap getImage(){
			return image;
		}
		
		private boolean determineMimeType(){
			if(mimeType.equalsIgnoreCase("application/vnd.google-apps.document")){
				return true;
				//BitmapFactory.decodeResource(getResources(), R.drawable.doc);
			}
			if(mimeType.equalsIgnoreCase("application/vnd.google-apps.presentation")){
				return true;
			}
			if(mimeType.equalsIgnoreCase("application/vnd.google-apps.spreadsheet")){
				return true;
			}
			if(mimeType.equalsIgnoreCase("application/vnd.google-apps.folder")){
				return false;
			}
			if(mimeType.equalsIgnoreCase("application/vnd.google-apps.drawing") ||
			   mimeType.equalsIgnoreCase("application/vnd.google-apps.photo")	||
			   mimeType.equalsIgnoreCase("application/vnd.google-apps.image") ){
				return false;
			}
			return false;
		}
		public boolean isViewedYet() {
			return viewedYet;
		}
		public void setViewedYet(boolean viewedYet) {
			this.viewedYet = viewedYet;
		}
		public String getMimeType() {
			return mimeType;
		}
	}
	//TODO: File Manager Class
	private class FileManager extends ArrayAdapter<FileDisplay>{
		private ArrayList<FileDisplay> files;
		private DriveClickListener clickListener;
		private List<String> path;
		//private Drive service;

		public FileManager(Context context, int textViewResourceId, ArrayList<FileDisplay> files, Drive service) {
			super(context, textViewResourceId, files);
			this.files = files;
			this.path = new ArrayList<String>();
			//this.service = service;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			//data
			Format format = new SimpleDateFormat("yy.MM.dd", Locale.US);
			FileDisplay f = files.get(position);
			Date dateData = new Date(f.getLastViewedByMe().getValue());
			ViewHolder holder;
			View v = convertView;
			clickListener = new DriveClickListener(f);
			if (v == null) {
				LayoutInflater vi = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(R.layout.row, null);
				
				holder = new ViewHolder();
				//graphical elements
				holder.title = (TextView) v.findViewById(R.id.toptext);
				holder.fileSize = (TextView) v.findViewById(R.id.bottomtext);
				holder.date = (TextView) v.findViewById(R.id.date);
				holder.icon = (ImageView) v.findViewById(R.id.icon);
				v.setOnClickListener(clickListener);
		        v.setTag(holder);
		    } else {
		    	if(((ViewGroup)v).getChildCount() > 4){
		    		DisplayMetrics metrics = new DisplayMetrics();
		    		getWindowManager().getDefaultDisplay().getMetrics(metrics);
					int defaultHeight = getDPI(64,metrics);
		    		//find the containing relative layout of a file
					RelativeLayout fileListing= (RelativeLayout) v.findViewById(R.id.filerow);
					//get settings for the layout of file
					ViewGroup.LayoutParams settings = fileListing.getLayoutParams();
					//set the width to the right size
					View optionsBar = fileListing.findViewById(R.id.options); 
					metrics = new DisplayMetrics();
					getWindowManager().getDefaultDisplay().getMetrics(metrics);

					settings.height = defaultHeight;
					//apply the change
					fileListing.setLayoutParams(new AbsListView.LayoutParams(settings));
					while (((ViewGroup)v).getChildCount() > 4){
						optionsBar = fileListing.findViewById(R.id.options);
						optionsBar.invalidate();
						fileListing.removeView(optionsBar);
					}
		    	}
		    	holder = (ViewHolder) convertView.getTag();   
		    }
			//start filling graphical elements according to data
			if (holder.title != null) {
				holder.title.setText(f.getTitle());           
			}
			if(holder.fileSize != null){
				Long l = (Long) f.getFileSize();
				holder.fileSize.setText(fileSizeFormat(l)); //probably have to apply formatting here
			}
			if(holder.date != null) {
				holder.date.setText("Last Opened: " + format.format(dateData));
			}
			if(holder.icon != null) {
				holder.icon.setImageBitmap(f.getImage());
			}
			holder.date.bringToFront();
			return v;
		}
		
		public int getDPI(int size, DisplayMetrics metrics){
		     return (size * metrics.densityDpi) / DisplayMetrics.DENSITY_DEFAULT;        
		 }
		private String fileSizeFormat(long size) {
		    if(size <= 1000) return "1 KB";
		    final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
		    int digitGroups = (int) (Math.log10(size)/Math.log10(1024));
		    return new DecimalFormat("#,##0.##").format(size/Math.pow(1024, digitGroups)) + " " + units[digitGroups];
		}
		//TODO: openFolder and closeFolder to update paths
		public void openFolder(File folder){
			
		}
	}
	private class ViewHolder {
		public TextView title;
		public TextView fileSize;
		public TextView date;
		public ImageView icon;
	}
	private class DriveClickListener implements OnClickListener{
		FileDisplay fileContents;
		boolean notExpanded = true;
		boolean heightSet = false;
		
		public DriveClickListener(FileDisplay fileContents){ this.fileContents = fileContents; }
		
		DisplayMetrics metrics = new DisplayMetrics();
		int defaultHeight;
		@Override
		public void onClick(View v) {
			if(!heightSet) {
				heightSet = true;
				metrics = new DisplayMetrics();
				getWindowManager().getDefaultDisplay().getMetrics(metrics);
				defaultHeight = getDPI(64,metrics);
			}
			//TODO: quick fix for getting rid of the multiple menu bars
			if(((ViewGroup)v).getChildCount() == 4){
				notExpanded = true;
			} else {
				notExpanded = false;
			}
			if(notExpanded) {
				expandView(v);
				notExpanded = false;
			} else {
				collapseView(v);
				notExpanded = true;
			}
		}
		public int getDPI(int size, DisplayMetrics metrics){
		     return (size * metrics.densityDpi) / DisplayMetrics.DENSITY_DEFAULT;        
		 }
		private View setupOptions(){
			LayoutInflater buttonLayout = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View optionsInit = buttonLayout.inflate(R.layout.buttonbar, null);
			
			Button open = (Button) optionsInit.findViewById(R.id.open);
			Button delete = (Button) optionsInit.findViewById(R.id.delete);
			Button rename = (Button) optionsInit.findViewById(R.id.rename);

			open.setOnClickListener(new MenuClickListener(fileContents, Actions.OPEN));
			
			delete.setOnClickListener(new MenuClickListener(fileContents, Actions.DELETE));
			
			rename.setOnClickListener(new MenuClickListener(fileContents, Actions.RENAME));
			//TODO: enable rename button
			
			return optionsInit;
		}
		private void expandView(View v){
			View options = setupOptions();			
			//find the containing relative layout of a file
			RelativeLayout fileListing= (RelativeLayout) v.findViewById(R.id.filerow);
			//get settings for the layout of file
			ViewGroup.LayoutParams settings = fileListing.getLayoutParams();
			//set the width to the right size
			metrics = new DisplayMetrics();
			getWindowManager().getDefaultDisplay().getMetrics(metrics);
			settings.height = defaultHeight + getDPI(30,metrics);

			//apply the change
			fileListing.setLayoutParams(settings);
			fileListing.addView(options);
			options = fileListing.getChildAt(4);
			fileListing.removeViewAt(4);
			RelativeLayout.LayoutParams optionBarSettings = new RelativeLayout.LayoutParams(options.getLayoutParams());
			optionBarSettings.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM);
			options.setLayoutParams(optionBarSettings);
			fileListing.addView(options);
		}
		protected void collapseView(View v) {
			//find the containing relative layout of a file
			RelativeLayout fileListing= (RelativeLayout) v.findViewById(R.id.filerow);
			//get settings for the layout of file
			RelativeLayout.LayoutParams settings = new RelativeLayout.LayoutParams(fileListing.getLayoutParams());
			//set the width to the right size
			View optionsBar = fileListing.findViewById(R.id.options); 
			metrics = new DisplayMetrics();
			getWindowManager().getDefaultDisplay().getMetrics(metrics);

			settings.height = defaultHeight;
			//apply the change
			fileListing.setLayoutParams(new AbsListView.LayoutParams(settings));
			while (((ViewGroup)v).getChildCount() > 4){
				optionsBar = fileListing.findViewById(R.id.options);
				optionsBar.invalidate();
				fileListing.removeView(optionsBar);
			}
		}
	}
	//TODO MenuClickListener class
	private class MenuClickListener implements OnClickListener {
		FileDisplay fileContents;
		Actions actionType;
		Drive service;
		public MenuClickListener (FileDisplay fileContents, Actions actionType){
			this.fileContents = fileContents;
			this.actionType = actionType;
			this.service = MainActivity.service;
		}
		@Override
		public void onClick(View v) {
			switch (actionType){
			case OPEN:
				showToast("Open!");
				//determine file type
				if (fileContents.getMimeType().equals("application/vnd.google-apps.folder")){
					//TODO: folder explorer
				} else {
					openFile();
				}
				break;
			case RENAME:
				showToast("Rename!");
				showToast("...!");
				showToast("Guess it's not working yet");
				break;
			case DELETE:
				showToast("Delete!");
				showToast("...!");
				showToast("Guess it's not working yet");
				break;
			default:
				showToast("oops! there's a bug somewhere...");
				break;

			}
		}
		@SuppressLint("SetJavaScriptEnabled")
		public void openFile() {
			/**
			 * Download a file's content.
			 *
			 * @param service Drive API service instance.
			 * @param file Drive File instance.
			 * @return InputStream containing the file's content if successful,
			 *         {@code null} otherwise.
			 */
			if (fileContents.getDownloadUrl() != null && fileContents.getDownloadUrl().length() > 0) {
				try {
					showToast("Opening file!");
					state = AppStatus.VIEW;
					//Intent webViewIntent = new Intent(MainActivity.this, ViewerActivity.class);
					//webViewIntent.putExtra(REFERENCE, fileContents.getDownloadUrl());
					//webViewIntent.putExtra(FILE_NUMBER, fileContents.getId());
					//startActivity(webViewIntent);
					
					//LayoutInflater vi = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					//WebView mWebView = (WebView) vi.inflate(R.layout.webview, null);
					//ListView newList = (ListView) findViewById(android.R.id.list);
					
					WebView mWebView = (WebView) LayoutInflater.from(MainActivity.this).inflate(R.layout.webview, null);;
					
					mWebView.clearView();

					mWebView.setWebViewClient(new WebViewClient());

					WebSettings webSettings = mWebView.getSettings();
					webSettings.setJavaScriptEnabled(true);
					webSettings.setPluginState(WebSettings.PluginState.ON);
					
					mWebView.loadUrl(fileContents.getDownloadUrl());
					myCustomWebViewer.addView(mWebView);
					setContentView(myCustomWebViewer);
					//mWebView.loadData("<h1> Test </h1>", "text/html; charset=UTF-8", null);
					//setContentView(newList);
				    //mWebView.loadUrl("https://docs.google.com/gview?embedded=true&url="+fileContents.getDownloadUrl());
				    showToast("...");//setContentView(mWebView);
				    showToast("Maybe...");
					/*
				    HttpResponse resp =
							this.service.getRequestFactory().buildGetRequest(new GenericUrl(fileContents.getDownloadUrl()))
							.execute();
					Intent pdfOpen = new Intent(Intent.ACTION_VIEW);
					pdfOpen.setDataAndType(Uri.parse(fileContents.getDownloadUrl()), "application/pdf");
					resp.getContent();
					startActivity(pdfOpen);
				} catch (IOException e) {
					// An error occurred.
					showToast("whaat D:");
					e.printStackTrace();//*/
				} finally { 
					setContentView(R.layout.activity_main);
				}
			} else {
				showToast("...");
				showToast("File was empty...");
				// The file doesn't have any content stored on Drive.
			}
		}
	}
	private class WebArray extends ArrayAdapter<WebView> {
		private ArrayList<WebView> files;
		
		//private Drive service;
		public WebArray (Context context, int textViewResourceId, ArrayList<WebView> file, Drive service) {
			super(context, textViewResourceId);
			this.files = file;
			//this.service = service;
		}
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			LayoutInflater vi = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			return vi.inflate(R.layout.row, null);
		}
	}
}
