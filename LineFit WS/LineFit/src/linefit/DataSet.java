/* Copyright (C) 2013 Covenant College Physics Department
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General License for more details.
 * 
 * You should have received a copy of the GNU Affero General License along with this program. If not, see
 * http://www.gnu.org/licenses/. */

package linefit;

import java.awt.Color;
import java.awt.Polygon;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import linefit.FitAlgorithms.FitType;
import linefit.FitAlgorithms.FixedVariable;
import linefit.FitAlgorithms.LinearFitStrategy;
import linefit.IO.ChangeTracker;
import linefit.IO.HasDataToSave;


/** The class that keeps track of the data in each DataSet in the GraphArea. It contains the columns with the x,y, and
 * error/uncertainty value as well as the color and shape of the Set. Other names include: GraphDataSet, DataSet,
 * GraphSet
 * 
 * @author Unknown
 * @version 1.0
 * @since &lt;0.98.0 */
public class DataSet extends JScrollPane implements HasDataToSave
{
    /** The static variable that keeps track of the current number of GraphDataSets in the GraphArea. Used to determine
     * the number used for the next GraphDataSet */
    private static int numberOfGraphDataSets = 0;

    /** The Serial Version UID so that we know what version it is when we are using it. See
     * http://docs.oracle.com/javase/7/docs/api/java/io/Serializable.html for full discussion on its uses and purpose */
    private final static long serialVersionUID = 42L;
    /** The Default number of columns in each GraphDataSet when it is created. By Default it is two: one for the x
     * values and one for the y values */
    final static int DEFAULT_NUMBER_OF_COLUMNS = 2;
    /** The Default number or rows in each column in the GraphDataSet */
    final static int DEFAULT_NUMBER_OF_ROWS = 10;

    /** The object that keeps track of if any changes have been made */
    private ChangeTracker changeTracker;

    /** The table that contains and allows us to input data */
    private JTable tableContainingData;
    /** The model of the Table to use for inputting and storing data */
    DataSetTableModel dataTableModel;
    /** The name of this graph set to be displayed to the user */
    private String dataSetName;
    /** If the current GraphDataSet is visible and should be drawn to the GraphArea */
    public boolean visibleGraph;

    /** The list of all the visible DataColumns in this DataSet */
    ArrayList<DataColumn> dataColumns;
    ArrayList<DataColumn> errorColumns;

    int errorColumnsDisplayed = 0;
    DataDimension[] errorColumnsOrder = DataDimension.values();

    /** The FitAlgrorithm we are using to fit this DataSet that also keeps track of the fit's data */
    public LinearFitStrategy linearFitStrategy; // TODO: encapsulate
    /** The currently selected FitType of this DataSet (i.e. no fit, x error fit) */
    private FitType dataSetFitType;
    /** The color of this DataSet when drawn to the GraphArea */
    private Color dataSetColor;
    /** The shape of this DataSet when drawn to the GraphArea */
    private Shape dataSetShape;
    /** The color selector that is chosen when the set is selected this way there is not multiple ones for the same
     * DataSet leading to some potentially awkward situations */
    private CustomColorMenu customColorMenu;

    /** Creates a new empty DataSet that is linked to the GraphArea
     * 
     * @param parentGraphArea The GraphArea that this DataSet belongs to and will be drawn to */
    DataSet(GraphArea parentGraphArea, ChangeTracker parentsChangeTracker)
    {
        changeTracker = parentsChangeTracker;

        dataSetFitType = FitType.NONE;
        visibleGraph = true;

        linearFitStrategy = LineFit.currentFitAlgorithmFactory.createNewLinearFitStartegy(this);

        dataSetName = "DataSet " + numberOfGraphDataSets;
        dataColumns = new ArrayList<DataColumn>();
        errorColumns = new ArrayList<DataColumn>();
        dataTableModel = new DataSetTableModel();
        tableContainingData = new JTable(dataTableModel);
        tableContainingData.setGridColor(Color.gray);

        // Clean up JTable to make cell selection work more like excel
        tableContainingData.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);

        tableContainingData.setRowSelectionAllowed(true);
        tableContainingData.setColumnSelectionAllowed(true);
        tableContainingData.setCellSelectionEnabled(true);
        tableContainingData.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        setViewportView(tableContainingData);

        for (int i = 0; i < DEFAULT_NUMBER_OF_ROWS; i++)
        {
            dataTableModel.insertRow(dataTableModel.getRowCount(), new Object[0]);
        }

        for (DataDimension dim : DataDimension.values())
        {
            dataColumns.add(dim.getColumnIndex(), new DataColumn(dim.getDisplayString(), changeTracker));
            errorColumns.add(dim.getColumnIndex(), new DataColumn(dim.getErrorDisplayString(), changeTracker));
        }

        for (int i = 0; i < DataDimension.getNumberOfDimensions(); i++)
        {
            dataTableModel.addColumn(dataColumns.get(i).getName());
        }

        dataTableModel.addTableModelListener(new GraphSetListener(parentGraphArea));

        tableContainingData.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0, false), "MY_CUSTOM_ACTION");
        dataSetColor = Color.BLACK;
        dataSetShape = new Rectangle2D.Double();

        numberOfGraphDataSets++;

        // Add our table listener for this DataSet
        new SpreadSheetAdapter(tableContainingData);
    }

    /** A private constructor for an empty DataSet that is only used to make a placeholder DataSet */
    private DataSet()
    {
    }

    /** Returns an empty DataSet with no initialization to be used for the new DataSet option in the drop down menu
     * 
     * @param displayed The String to display on the DataSet drop down placeholder object
     * @return Returns a DataSet object to be put in the DataSetSelector to hold the place for creating a new DataSet */
    static DataSet createDropDownPlaceHolder(String displayed)
    {
        DataSet placeHolder = new DataSet();
        placeHolder.dataSetName = displayed;
        return placeHolder;
    }

    public void setErrorColumnOrder(DataDimension[] columnOrder)
    {
        int numCurrentlyDisplayed = errorColumnsDisplayed;
        while (errorColumnsDisplayed > 0)
        {
            hideLastErrorColumn();
        }

        errorColumnsOrder = columnOrder;

        while (errorColumnsDisplayed < numCurrentlyDisplayed)
        {
            showNextErrorColumn();
        }
    }

    public boolean setNumberOfDisplayedColumns(int numColumns)
    {
        int errorColums = numColumns - DataDimension.getNumberOfDimensions();
        if (errorColums < 0 || errorColums > DataDimension.getNumberOfDimensions())
        {
            return false;
        }
        else
        {
            int diffColumns = errorColums - errorColumnsDisplayed;
            boolean addColumns = diffColumns > 0;
            for (int i = 0; i < Math.abs(diffColumns); i++)
            {
                if (addColumns)
                {
                    showNextErrorColumn();
                }
                else
                {
                    hideLastErrorColumn();
                }
            }

            return true;
        }
    }

    public void showNextErrorColumn()
    {
        if (errorColumnsDisplayed < DataDimension.getNumberOfDimensions())
        {
            DataColumn error = errorColumns.get(errorColumnsOrder[errorColumnsDisplayed++].getColumnIndex());
            dataTableModel.addColumn(error.getName());
        }
    }

    public void hideLastErrorColumn()
    {
        // not working???
        if (errorColumnsDisplayed > 0)
        {
            dataTableModel.removeLastColumn();
            errorColumnsDisplayed--;
        }
    }

    /** Updates the available FitTypes we can use on this DataSet based on the amount of data in them */
    ArrayList<FitType> getAllowableFits()
    {
        ArrayList<FitType> fits = new ArrayList<FitType>();

        fits.add(FitType.NONE);
        ArrayList<Integer> validPoints = getIndexesOfValidPoints();
        if (validPoints.size() > 1)
        {
            fits.add(FitType.REGULAR);

            if (checkAllHaveErrors(validPoints, DataDimension.X))
            {
                fits.add(FitType.X_ERROR);

                if (checkAllHaveErrors(validPoints, DataDimension.Y))
                {
                    fits.add(FitType.Y_ERROR);
                    fits.add(FitType.BOTH_ERRORS);
                }
            }
            else if (checkAllHaveErrors(validPoints, DataDimension.Y))
            {
                fits.add(FitType.Y_ERROR);
            }
        }

        return fits;
    }

    /** Recalculates the FitData with our current FitType and data */
    void refreshFitData()
    {
        linearFitStrategy.refreshFitData();
    }

    /** returns how many rows have valid points, meaning that they have both x and y data for
     * 
     * @return The number of points containing at least an x and a y value in this DataSet */
    private ArrayList<Integer> getIndexesOfValidPoints()
    {
        ArrayList<Integer> validPoints = new ArrayList<Integer>();
        boolean pointValid;
        for (int i = 0; i < dataColumns.get(0).getData().size(); i++)
        {
            pointValid = true;
            for (int column = 0; column < DataDimension.getNumberOfDimensions(); column++)
            {
                if (dataColumns.get(column).isNull(i))
                {
                    pointValid = false;
                    break;
                }
            }

            if (pointValid)
            {
                validPoints.add(i);
            }
        }

        return validPoints;
    }

    public double[] getMinMax(DataDimension dim, boolean withErrors)
    {
        boolean hasInit = false;
        double dataMax = 0;
        double dataMin = 0;

        refreshFitData();
        DataColumn data = dataColumns.get(dim.getColumnIndex());
        DataColumn error = errorColumns.get(dim.getColumnIndex());

        for (int i = 0; i < data.getData().size(); i++)
        {
            if (!data.isNull(i))
            {
                double tmp = data.readDouble(i);
                double tmpErr = 0;
                if (withErrors && !error.isNull(i))
                {
                    tmpErr = Math.abs(error.readDouble(i));
                }

                if (hasInit)
                {
                    if (tmp + tmpErr > dataMax)
                    {
                        dataMax = tmp + tmpErr;
                    }
                    else if (tmp - tmpErr < dataMin)
                    {
                        dataMin = tmp - tmpErr;
                    }
                }
                else
                {
                    dataMax = tmp + tmpErr;
                    dataMin = tmp - tmpErr;
                    hasInit = true;
                }
            }
        }

        return new double[] { dataMin, dataMax };
    }

    /** Checks if all the points that have an x and a y also have an associated y error/uncertainty value. This is used
     * to determine if we can do a fit with y errors/uncertainties
     * 
     * @return True if all the points of this DataSet have a y error/uncertainty associated with them and false
     *         otherwise */
    private boolean checkAllHaveErrors(ArrayList<Integer> indexes, DataDimension dimension)
    {
        DataColumn error = errorColumns.get(dimension.getColumnIndex());

        for (Integer index : indexes)
        {
            if (error.isNull(index))
            {
                return false;
            }
        }

        return true;
    }

    /** Updates the DataColumns in this DataSet to make sure they are displaying the correct values in their cells and
     * formatted in a double format */
    void updateCellFormattingInColumns()
    {
        for (int i = 0; i < dataColumns.size(); i++)
        {
            DataColumn currentColumn = dataColumns.get(i);
            ArrayList<Double> columnData = currentColumn.getData();

            Iterator<Double> columnIterator = columnData.iterator();
            int rowNum = 0;

            while (columnIterator.hasNext())
            {
                Double valueInRow = columnIterator.next();
                dataTableModel.setValueAt(valueInRow, rowNum, i);
                rowNum++;
            }
        }
    }

    /** Determines whether or not their is data in this DataSet
     * 
     * @return Returns true if there is data and false if no data was found */
    public boolean hasData()
    {
        return dataTableModel.hasData();
    }

    /** Determines whether or not there is a visible CustomColorMenu for this DataSet or if it does not exist or is
     * hidden from view
     * 
     * @return Returns true if there is a CustomColorMenu and it is Visible */
    boolean doesHaveVisibleCustomColorMenu()
    {
        // return if it is not null and it is visible
        return customColorMenu != null && customColorMenu.isVisible();
    }

    /** Creates a new CustomColorMenu for this DataSet, but only if one does not already exist. If one does then it
     * focuses on that CustomColorMenu
     * 
     * @return Returns the CustomColorMenu that is associated with this DataSet whether it is newly created or already
     *         existed */
    CustomColorMenu createOrFocusOnCustomColorMenu()
    {
        // if we have one bring it up, otherwise make one
        if (customColorMenu != null)
        {
            // if its just invisible then initialize it so it updates the color and makes it visible
            if (!customColorMenu.isVisible())
            {
                customColorMenu.initialize();
            }
            // bring it to the front
            customColorMenu.toFront();
        }
        else
        {
            customColorMenu = new CustomColorMenu(this);
        }
        return customColorMenu;
    }

    /** Reads in data or an option related to the data from the passed in line
     * 
     * @param line The line that contains the data or option related to the data
     * @param newDataSet Signals that the line passed in is the beginning of a new data set
     * @return Returns true if the data or option for the data was read in from the line */
    public boolean readInDataAndDataOptions(String line, boolean unused)
    {
        // now split the input into the two parts
        // we can't use split because it will mess up on names as well as points since they have multiple spaces
        int firstSpaceIndex = line.indexOf(' ');
        String field = line.substring(0, firstSpaceIndex).toLowerCase();
        String valueForField = line.substring(firstSpaceIndex + 1).toLowerCase();

        boolean found = true;
        try
        {
            switch (field)
            {
                case "colnum":
                case "numberofcolumns":
                {
                    // Not used anymore
                    break;
                }
                case "fittype":
                {
                    // loop through all the fit types checking them against their toString methods
                    boolean foundFitType = false;
                    for (FitType ft : FitType.values())
                    {
                        if (valueForField.equals(ft.toString().toLowerCase()))
                        {
                            foundFitType = true;
                            this.setFitType(ft);
                            break;
                        }
                    }
                    // if we didn't find it for whatever strange reason, default to none
                    if (!foundFitType)
                    {
                        this.setFitType(FitType.NONE);
                    }
                    break;
                }
                case "whatisfixed":
                {
                    // loop though all the fixed variables checking them against the toString methods
                    boolean foundFixedVariable = false;
                    for (FixedVariable fv : FixedVariable.values())
                    {
                        if (valueForField.equals(fv.toString().toLowerCase()))
                        {
                            linearFitStrategy.setWhatIsFixed(fv, linearFitStrategy.getFixedValue());
                            foundFixedVariable = true;
                            break;
                        }
                    }
                    // if we didnt find anyone just default to none
                    if (!foundFixedVariable)
                    {
                        linearFitStrategy.setWhatIsFixed(FixedVariable.NONE, linearFitStrategy.getFixedValue());
                    }
                    break;
                }

                case "fixedvalue":
                    linearFitStrategy.setWhatIsFixed(linearFitStrategy.getWhatIsFixed(), Double.parseDouble(
                            valueForField));
                    break;
                case "visible":
                    visibleGraph = valueForField.toLowerCase().equals("true");
                    break;
                case "shape":
                {
                    if (valueForField.equals("rectangle"))
                    {
                        setShape(new Rectangle2D.Double());
                    }
                    else if (valueForField.equals("circle"))
                    {
                        setShape(new Ellipse2D.Double());
                    }
                    else
                    {
                        setShape(new Polygon());
                    }
                    break;
                }
                case "color":
                {
                    switch (valueForField)
                    {
                        case "black":
                            setColor(Color.BLACK);
                            break;
                        case "yellow":
                            setColor(Color.YELLOW);
                            break;
                        case "blue":
                            setColor(Color.BLUE);
                            break;
                        case "green":
                            setColor(Color.GREEN);
                            break;
                        case "orange":
                            setColor(Color.ORANGE);
                            break;
                        case "red":
                            setColor(Color.RED);
                            break;
                        default:// we expect three ints
                        {
                            String[] colorInputExploded = valueForField.split(" ");
                            if (colorInputExploded.length == 3)
                            {
                                // get the rgb as ints and set up the color
                                try
                                {
                                    int red = Integer.parseInt(colorInputExploded[0]);
                                    int green = Integer.parseInt(colorInputExploded[1]);
                                    int blue = Integer.parseInt(colorInputExploded[2]);
                                    setColor(new Color(red, green, blue));
                                }
                                catch (NumberFormatException e)
                                {
                                    setColor(Color.BLACK);
                                }
                            }
                            else
                            {
                                setColor(Color.BLACK);
                            }
                            break;
                        }
                    }
                    break;
                }
                case "colname":
                    break; // we don't use this anymore but we don't want to cause errors when reading old files int.
                           // visibleDataColumns.get(colNum).setName(valueForField); break;
                case "coldesc":
                    break; // we don't use this anymore but we don't want to cause errors when reading old files in
                case "p":
                case "datapoint":
                {
                    // split it up into the separate string parts
                    String[] splitPointValuesInput = valueForField.split(" ");

                    // Reads should only take place when a set is created so we
                    // can just use the data size of the first column to determine
                    // the next row to add at.
                    int row = dataColumns.get(0).dataSize();
                    for (int column = 0; column < splitPointValuesInput.length; column++)
                    {
                        String pointValueString = splitPointValuesInput[column];

                        Double value = null;

                        if (!pointValueString.equals("null"))
                        {
                            value = Double.parseDouble(pointValueString);
                        }
                        try
                        {
                            dataColumns.get(column).writeData(row, pointValueString);
                            dataTableModel.setValueAt(value, row, column);
                        }
                        catch (IndexOutOfBoundsException iobe)
                        {
                            System.err.println(
                                    "Error reading in DataPoint - More values specified than columns - Continuing: " +
                                            line);
                            break;
                        }
                    }
                    break;
                }
                default:
                    found = false;
                    break;
            }

        }
        catch (NumberFormatException nfe)
        {
            JOptionPane.showMessageDialog(this, "Error reading in number from line: " + line, "NFE Error",
                    JOptionPane.ERROR_MESSAGE);
        }

        return found;
    }

    /** Retrieve all the data and options associated with the data in the passed in array lists
     * 
     * @param variableNames The ArrayList of the names of the options
     * @param variableValues The ArrayList of the values of the options (indexed matched to the names) */
    public void retrieveAllDataAndDataOptions(ArrayList<String> variableNames, ArrayList<String> variableValues)
    {
        if (dataTableModel.hasData())
        {
            variableNames.add("FitType");
            variableValues.add(dataSetFitType.toString());
            variableNames.add("WhatIsFixed");
            if (linearFitStrategy.getWhatIsFixed() == FixedVariable.SLOPE)
            {
                variableValues.add("slope");
            }
            else if (linearFitStrategy.getWhatIsFixed() == FixedVariable.INTERCEPT)
            {
                variableValues.add("intercept");
            }
            else
            {
                variableValues.add("none");
            }

            variableNames.add("FixedValue");
            variableValues.add(Double.toString(linearFitStrategy.getFixedValue()));
            variableNames.add("Visible");
            variableValues.add(Boolean.toString(visibleGraph));
            variableNames.add("Shape");
            variableValues.add(getShapeString());
            variableNames.add("Color");
            variableValues.add(getColorString());

            String datapoint;
            ArrayList<Integer> indexes = getIndexesOfValidPoints();
            for (Integer index : indexes)
            {
                datapoint = "";
                variableNames.add("DataPoint");
                for (int j = 0; j < DataDimension.getNumberOfDimensions(); j++)
                {
                    if (j > 0)
                    {
                        datapoint += " ";
                    }
                    datapoint += dataColumns.get(j).getData().get(index);
                }

                for (int j = 0; j < errorColumnsDisplayed; j++)
                {
                    datapoint += " " + errorColumns.get(j).getData().get(index);
                }

                variableValues.add(datapoint);
            }
        }
    }

    /** Returns the DataSet's name */
    public String toString()
    {
        return dataSetName;
    }

    // getters and setters
    /** Returns the Chi squared value for this dataset's current fit
     * 
     * @return The Chi squared as a double */
    public double getChiSquared()
    {
        return this.linearFitStrategy.calculateChiSquared(this.linearFitStrategy.getSlope(), this.linearFitStrategy
                .getIntercept());
    }

    /** Gets the shape this DataSet as a String is using when being draw to the GraphArea
     * 
     * @return A String representing the shape of the points in this DataSet */
    public String getShapeString()
    {
        String output = "";

        Rectangle2D.Double rect = new Rectangle2D.Double();
        Ellipse2D.Double circ = new Ellipse2D.Double();
        Polygon tri = new Polygon();

        if (dataSetShape.getClass() == rect.getClass())
        {
            output = "rectangle";
        }
        else if (dataSetShape.getClass() == circ.getClass())
        {
            output = "circle";
        }
        else if (dataSetShape.getClass() == tri.getClass())
        {
            output = "triangle";
        }
        else
        {
            output = "rectangle";
        }
        return output;
    }

    /** Gets the color of this DataSet as a String that it is drawn with on the GraphArea
     * 
     * @return A String representing this DataSet's color */
    public String getColorString()
    {
        if (dataSetColor == Color.BLACK)
        {
            return "black";
        }
        else if (dataSetColor == Color.YELLOW)
        {
            return "yellow";
        }
        else if (dataSetColor == Color.BLUE)
        {
            return "blue";
        }
        else if (dataSetColor == Color.GREEN)
        {
            return "green";
        }
        else if (dataSetColor == Color.ORANGE)
        {
            return "orange";
        }
        else if (dataSetColor == Color.RED)
        {
            return "red";
        }
        else
        {
            return dataSetColor.getRed() + " " + dataSetColor.getGreen() + " " + dataSetColor.getBlue();
        }
    }

    public int getNumberOfDisplayedColumns()
    {
        return DataDimension.getNumberOfDimensions() + errorColumnsDisplayed;
    }

    /** Gets the current Color that is being used by this DataSet
     * 
     * @return The Color that is being used to draw this DataSet */
    public Color getColor()
    {
        return dataSetColor;
    }

    /** Gets the shape of the points used when drawing to the GraphArea
     * 
     * @return The Shape to use when drawing this DataSet to the GraphArea */
    public Shape getShape()
    {
        return dataSetShape;
    }

    /** Gets the Table that contains the data for this DataSet
     * 
     * @return The Table containing this DataSet's data */
    public JTable getDataTable()
    {
        return tableContainingData;
    }

    /** Gets the name of this DataSet
     * 
     * @return The String containing this DataSet's name */
    public String getName()
    {
        return dataSetName;
    }

    /** Gets the FitType that this DataSet is using
     * 
     * @return The FitType this DataSet is using */
    public FitType getFitType()
    {
        return dataSetFitType;
    }

    public DataColumn getColumn(int columnIndex)
    {
        if (columnIndex < DataDimension.getNumberOfDimensions())
        {
            return getData(columnIndex);
        }
        else
        {
            return getErrorData(errorColumnsOrder[columnIndex - DataDimension.getNumberOfDimensions()]);
        }
    }

    /** The DataColumn that keeps track of the x data for this DataSet
     * 
     * @return The DataColumn that keeps track of the x data values for this DataSet */
    public DataColumn getData(int index)
    {
        return dataColumns.get(index);
    }

    public DataColumn getData(DataDimension data)
    {
        return getData(data.getColumnIndex());
    }

    /** The DataColumn that keeps track of the x data for this DataSet
     * 
     * @return The DataColumn that keeps track of the x data values for this DataSet */
    public DataColumn getErrorData(int errorColumnIndex)
    {
        return errorColumns.get(errorColumnIndex);
    }

    public DataColumn getErrorData(DataDimension data)
    {
        return getErrorData(data.getColumnIndex());
    }

    /** Sets the Color to be used when drawing this DataSet to the given Color
     * 
     * @param color The desired Color to use when drawing this DataSet to the GraphArea */
    public void setColor(Color color)
    {
        changeTracker.setFileModified();
        dataSetColor = color;
    }

    /** Sets the shape used for the points of this DataSet when drawing it to the GraphArea to the given Shape
     * 
     * @param shape The desired Shape to use when drawing this DataSet's points */
    public void setShape(Shape shape)
    {
        changeTracker.setFileModified();
        dataSetShape = shape;
    }

    /** Sets the FitType to use for this DataSet to the given FitType
     * 
     * @param fit The FitType to use for this DataSet's linear fit */
    public void setFitType(FitType fit)
    {
        changeTracker.setFileModified();
        dataSetFitType = fit;
    }

    /** Sets the name of this DataSet to the desired passed name
     * 
     * @param name The new Name of this DataSet */
    public void setName(String name)
    {
        dataSetName = name;
    }

    // private classes
    /** A Listener class on the DataSet table that allows us to update the GraphArea whenever we make changes to the
     * columns
     * 
     * @author Keith Rice
     * @version 1.0
     * @since &lt;0.98.0 */
    private class GraphSetListener implements TableModelListener
    {
        GraphArea graphArea;

        GraphSetListener(GraphArea area)
        {
            graphArea = area;
        }

        /** The event that is called whenever the values in the table have been modified */
        public void tableChanged(TableModelEvent e)
        {
            if (e.getColumn() >= 0)
            {
                DataColumn data = getColumn(e.getColumn());
                data.writeData(e.getFirstRow(), dataTableModel.getValueAt(e.getFirstRow(), e.getColumn()).toString());

                // if there are no more rows, then add one
                if (e.getFirstRow() + 1 == dataTableModel.getRowCount())
                {
                    dataTableModel.addRow(new Object[dataTableModel.getColumnCount()]);
                }

                refreshFitData();
                graphArea.repaint();
            }
        }
    }
}