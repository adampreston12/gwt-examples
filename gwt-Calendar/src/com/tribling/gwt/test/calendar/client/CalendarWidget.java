package com.tribling.gwt.test.calendar.client;

import java.util.Date;

import com.google.gwt.user.client.ui.ChangeListener;
import com.google.gwt.user.client.ui.ChangeListenerCollection;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PushButton;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

public class CalendarWidget extends Composite implements ClickListener {

	/**
	 * Make a day widget with clicklistener to put in the calendar days
	 */
	public class DayWidget extends Composite implements ClickListener {

		// change listeners for this widget
		private ChangeListenerCollection changeListeners;

		FocusPanel pContent = new FocusPanel();
		FocusPanel pDayNumber = new FocusPanel();
		VerticalPanel pDayPanel = new VerticalPanel();

		int SelectedDay = 0;

		/**
		 * constructor
		 */
		public DayWidget() {

			//TODO - set cursor over text

			//create focus panels that observe
			pDayNumber.add(new Label(Integer.toString(SelectedDay)));
			//pContent.add(new Label("test"));

			//add the focus panels to the main panel
			pDayPanel.add(pDayNumber);
			//pDayPanel.add(pContent);

			//init the focus panel widget
			initWidget(pDayPanel);

			pDayNumber.addClickListener(this);
			//pContent.addClickListener(this);

			pDayNumber.setWidth("100%");
			//pContent.setWidth("100%");
			pDayPanel.setWidth("100%");
		}

		/**
		 * use this to listen/observe to the widget
		 * 
		 * @param listener
		 */
		protected void addChangeListener(ChangeListener listener) {
			if (changeListeners == null)
				changeListeners = new ChangeListenerCollection();
			changeListeners.add(listener);
		}

		public int getDaySelected() {
			return this.SelectedDay;
		}

		public void onClick(Widget sender) {

			//Window.alert("click in day widget: Day: " + this.SelectedDay);

			//notify the orginator widget, to look for getDayNumber
			if (changeListeners != null) {
				changeListeners.fireChange(this);
			}
		}

		protected void removeChangeListener(ChangeListener listener) {
			if (changeListeners != null)
				changeListeners.remove(listener);
		}

		public void setDayNumber(int DayNumber) {
			this.SelectedDay = DayNumber;
			pDayNumber.clear();
			pDayNumber.add(new Label(Integer.toString(this.SelectedDay)));
		}

		public void setDayPanelStyle(String Style) {
			pDayPanel.addStyleName(Style);
			//pDayNumber.addStyleName(Style);
		}

		public void setDaySelected(int DaySelected) {
			this.SelectedDay = DaySelected;
		}
	}
	
	
	
	PushButton bNextMonth = new PushButton(">");
	PushButton bNextYear = new PushButton(">");
	PushButton bPrevMonth = new PushButton("<");

	PushButton bPrevYear = new PushButton("<");

	//Observer to notify the originator object
	private ChangeListenerCollection changeListeners;

	//First day falls on what weekday s,m,t,w,t,f,s
	private int dayOfMonthOffset = 0;

	//Days of Week
	private String[] daysOfWeek = new String[] { "Sunday", "Monday", "Tuesday",
			"Wednesday", "Thursday", "Friday", "Saturday" };

	//Days of Week Abrev
	private String[] daysOfWeekAbrev = new String[] { "Sun", "Mon", "Tue",
			"Wed", "Thu", "Fri", "Sat" };

	private DayWidget dayWidget;

	//Num the first day of month falls on
	private int firstDayNumOfMonth = 0;

	//Days in month
	private int lastDayInMonth = 0;

	//Months
	private String[] monthNames = new String[] { "January", "February",
			"March", "April", "May", "June", "July", "August", "September",
			"October", "November", "December" };
	//calendar content
	HorizontalPanel pCalendarContent = new HorizontalPanel();
	//calendar menu
	HorizontalPanel pCalendarMenu = new HorizontalPanel();
	//calendar widget
	VerticalPanel pCalendarWidget = new VerticalPanel();
	HorizontalPanel pMenuMonth = new HorizontalPanel();
	HorizontalPanel pMenuYear = new HorizontalPanel();

	HorizontalPanel pMonthName = new HorizontalPanel();
	HorizontalPanel pYear = new HorizontalPanel();

	//Selected DaY
	private int selectedDay = 0;
	//Menu Selected
	private int selMonth = 0;
	private int selYear = 0;

	//Today
	private int todayDate = 0;

	private int todayMonth = 0;

	private int todayYear = 0;

	/**
	 * constructor - make widget
	 */
	public CalendarWidget() {

		// menu
		pCalendarMenu.addStyleName("cal-menu");
		pCalendarMenu.setHorizontalAlignment(HorizontalPanel.ALIGN_CENTER);
		pCalendarMenu.add(pMenuMonth);
		pCalendarMenu.add(new HTML(
				"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"));
		pCalendarMenu.add(pMenuYear);

		//combine the menu and calendar
		VerticalPanel combine = new VerticalPanel();
		combine.add(pCalendarMenu);
		combine.add(pCalendarContent);
		pCalendarWidget.add(combine);

		//init the widget
		initWidget(pCalendarWidget);

		bPrevMonth.addClickListener(this);
		bNextMonth.addClickListener(this);
		bPrevYear.addClickListener(this);
		bNextYear.addClickListener(this);

		//get todays date
		getToday();

		//set the first month to this month
		setSelectedFirst();

		//draw menu
		drawCalendarMenu();

		//draw the first calendar
		drawCalendarGrid();

		//debug();
	}

	/**
	 * use this to listen/observe to the widget
	 * 
	 * @param listener
	 */
	protected void addChangeListener(ChangeListener listener) {
		if (changeListeners == null)
			changeListeners = new ChangeListenerCollection();
		changeListeners.add(listener);
	}

	/**
	 * calculate common calendar stuff
	 */
	private void calcCalendar() {
		getLastDayInMonth();
		getFirstDayNumOfMonth();
	}

	private void changeNextMonth() {

		// if dec to jan
		if (selMonth == 11) {
			selYear = selYear + 1;
			selMonth = 0;
			drawCalendarYear();
		} else {
			selMonth = selMonth + 1;
		}

		//change month name
		drawCalendarMonth();

		//redraw calendar
		drawCalendarGrid();
	}

	private void changeNextYear() {
		selYear = selYear + 1;
		drawCalendarYear();

		//redraw calendar
		drawCalendarGrid();
	}

	private void changePrevMonth() {

		// if jan to dec
		if (selMonth == 0) {
			selYear = selYear - 1;
			selMonth = 11;
			drawCalendarYear();
		} else {
			selMonth = selMonth - 1;
		}

		//change month name
		drawCalendarMonth();

		//redraw calendar
		drawCalendarGrid();
	}

	private void changePrevYear() {
		selYear = selYear - 1;
		drawCalendarYear();

		//redraw calendar
		drawCalendarGrid();
	}

	private void debug() {
		VerticalPanel vp = new VerticalPanel();
		vp.add(new Label("selYear: " + selYear));
		vp.add(new Label("todayYear: " + todayYear));
		vp.add(new Label("selMonth: " + selMonth));
		vp.add(new Label("selMonth: " + todayMonth));
		vp.add(new Label("FirstDayOfMonth: " + firstDayNumOfMonth));
		vp.add(new Label("lastDayInMonth: " + lastDayInMonth));
		RootPanel.get().add(vp);
	}

	/**
	 * draw calendar grid
	 */
	private void drawCalendarGrid() {
		pCalendarContent.clear(); //start over

		//calculate calendar days
		calcCalendar();

		int CountCells = 0 - 6; //consider first 7 cells names and 0 in day count
		int CountWeekDays = 0;
		int CountDays = 0;
		int LastDay = lastDayInMonth + firstDayNumOfMonth + 1;

		int Height = 6;
		if (firstDayNumOfMonth > 4 && lastDayInMonth > 30) {
			Height = 7; //depth of grid cells
		}

		int Width = 7; //width of grid cells
		Grid grid = new Grid(Height, Width);

		//set style for grid - can use css, but for this will do this for now
		//grid.setBorderWidth(1);
		grid.addStyleName("cal-grid");

		//height
		for (int row = 0; row < Height; ++row) {

			//width
			for (int column = 0; column < Width; ++column) {

				//width of cells
				grid.getCellFormatter().setWidth(row, column, "50px");

				//grid.setText(row, col, "" + row + ", " + col); //like 0,0 0,1 0,2

				//FIRST ROW
				//draw weekday names
				if (row == 0) {
					String WeekDayAbrev = getWeekDayName(CountWeekDays);
					grid.setText(row, column, WeekDayAbrev);
					grid.getCellFormatter().addStyleName(row, column,
							"cal-WeekDays");
					CountWeekDays++;
				}

				//AFTER ROW FIRST
				//draw Days with Number
				if (row > 0 && CountCells > firstDayNumOfMonth
						&& CountCells < LastDay) {
					//add dayNum
					String DayNum = Integer.toString(CountDays + 1);

					//TODAY
					String Style = "";
					if ((CountDays + 1 == todayDate && selMonth == todayMonth && selYear == todayYear)) {
						Style = "cal-Today";
					} else { //Other Days
						Style = "cal-Day";
					}

					//add a widget that observers for clicks
					dayWidget = new DayWidget();
					dayWidget.setDayPanelStyle(Style);
					dayWidget.setDayNumber(CountDays + 1);

					//observer the click on the day
					dayWidget.addChangeListener(new ChangeListener() {
						public void onChange(Widget sender) {
							DayWidget dw = (DayWidget) sender;//cast to type

							//Window.alert("Day Selected" + dw.getDaySelected());
							//set the day number clicked and then fire change
							setSelectedDay(dw.getDaySelected());
						}
					});

					//add day widget to grid
					grid.setWidget(row, column, dayWidget);

					CountDays++;
				}
				//count what cell we are on
				CountCells++;
			}
		}

		//add the grid to the widget
		pCalendarContent.add(grid);
	}

	private void drawCalendarMenu() {
		pMenuMonth.clear();
		pMenuMonth.add(bPrevMonth);
		pMenuMonth.add(new HTML("&nbsp;&nbsp;"));
		pMenuMonth.add(pMonthName);
		pMenuMonth.add(new HTML("&nbsp;&nbsp;"));
		pMenuMonth.add(bNextMonth);

		pMenuYear.clear();
		pMenuYear.add(bPrevYear);
		pMenuYear.add(new HTML("&nbsp;&nbsp;"));
		pMenuYear.add(pYear);
		pMenuYear.add(new HTML("&nbsp;&nbsp;"));
		pMenuYear.add(bNextYear);

		drawCalendarMonth();
		drawCalendarYear();
	}

	private void drawCalendarMonth() {
		String MonthName = getMonthName();
		pMonthName.clear();
		pMonthName.add(new Label(MonthName));
	}

	private void drawCalendarYear() {
		String Year = Integer.toString(selYear);
		pYear.clear();
		pYear.add(new Label(Year));
	}

	/**
	 * get where the first day of the month starts
	 */
	private void getFirstDayNumOfMonth() {
		Date date = new Date(selYear - 1900, selMonth, 1);
		firstDayNumOfMonth = date.getDay();
	}

	/**
	 * get days in month
	 */
	private void getLastDayInMonth() {

		switch (selMonth) {
		case 1:
			if ((selYear % 4 == 0 && selYear % 100 != 0) || selYear % 400 == 0) {
				lastDayInMonth = 29;
			} else {
				lastDayInMonth = 28;
			}
			break;
		case 3:
			lastDayInMonth = 30;
			break;
		case 5:
			lastDayInMonth = 30;
			break;
		case 8:
			lastDayInMonth = 30;
			break;
		case 10:
			lastDayInMonth = 30;
			break;
		default:
			lastDayInMonth = 31;
			break;
		}
	}

	private String getMonthName() {
		return monthNames[selMonth];
	}

	public int getSelectedDay() {
		return this.selectedDay;
	}

	/**
	 * get today info
	 */
	private void getToday() {
		Date date = new Date();
		todayYear = date.getYear() + 1900;
		todayMonth = date.getMonth();
		todayDate = date.getDate();
	}

	private String getWeekDayName(int WeekDay) {
		return daysOfWeekAbrev[WeekDay];
	}

	public void onClick(Widget sender) {

		//debug
		//Window.alert("click");

		//clear selected
		setSelectedDay(0);

		if (sender == bPrevMonth) {
			changePrevMonth();
		}

		if (sender == bNextMonth) {
			changeNextMonth();
		}

		if (sender == bPrevYear) {
			changePrevYear();
		}

		if (sender == bNextYear) {
			changeNextYear();
		}

		//any clicks here will notify the originator object
		if (changeListeners != null) {
			changeListeners.fireChange(this);
		}
	}

	protected void removeChangeListener(ChangeListener listener) {
		if (changeListeners != null)
			changeListeners.remove(listener);
	}

	private void setSelectedDay(int selectedDay) {
		this.selectedDay = selectedDay;

		if (changeListeners != null) {
			changeListeners.fireChange(this);
		}
	}

	/**
	 * set the default month to load
	 */
	private void setSelectedFirst() {
		selMonth = todayMonth;
		selYear = todayYear;
	}
}
