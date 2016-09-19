package lof.pruning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import org.apache.hadoop.mapreduce.Reducer.Context;

import metricspace.MetricObjectMore;
import metricspace.Record;

public abstract class prQuadNodeMore {
	private float[] xyCoordinates;
	private int[] indexInSmallCell;
	private prQuadNodeMore parentNode = null;
	private int numSmallCellX = 0;
	private int numSmallCellY = 0;
	private float smallCellSize = 0.0f;

	public prQuadNodeMore(float[] xyCoordinates, int[] indexInSmallCell, prQuadNodeMore parentNode, int numSmallCellsX,
			int numSmallCellsY, float smallCellSize) {
		this.xyCoordinates = xyCoordinates;
		this.indexInSmallCell = indexInSmallCell;
		this.parentNode = parentNode;
		this.numSmallCellX = numSmallCellsX;
		this.numSmallCellY = numSmallCellsY;
		this.smallCellSize = smallCellSize;
	}

	/**
	 * check if these two ranges overlap?
	 * 
	 * @param expectedRange
	 * @param checkedRange
	 * @return
	 */
	boolean checkRange(double[] expectedRange, double[] checkedRange) {
		// check (x1,y2) and (x2,y1)
		if (expectedRange[0] > checkedRange[1] || checkedRange[0] > expectedRange[1])
			return false;
		if (expectedRange[3] < checkedRange[2] || checkedRange[3] < expectedRange[2])
			return false;
		return true;
	}

	public void generateChilden(HashMap<Long, MetricObjectMore> CanPrunePoints, prQuadInternalMore curPRNode,
			Stack<prQuadInternalMore> prQuadTree,
			HashMap<prQuadInternalMore, ArrayList<MetricObjectMore>> mapQuadInternalWithPoints,
			ArrayList<prQuadLeafMore> prLeaves, float[] largeCellCoor, int numSmallInLargeX, int numSmallInLargeY,
			int K) {
		int count = 0;
		int numX_1 = (int) Math.ceil(curPRNode.getNumSmallCellX() / 2);
		int numX_2 = curPRNode.getNumSmallCellX() - numX_1;
		int numY_1 = (int) Math.ceil(curPRNode.getNumSmallCellY() / 2);
		int numY_2 = curPRNode.getNumSmallCellY() - numY_1;
		int[] numCellsPerDimX = { numX_1, numX_2 };
		int[] numCellsPerDimY = { numY_1, numY_2 };

		// init small cell index (the begining index of the second one)
		// if we have index(3,9), then the first one contains (3,6) the second
		// one contains(7,9)
		int[] midSmallIndex = { curPRNode.getIndexInSmallCell()[0] + numX_1,
				curPRNode.getIndexInSmallCell()[2] + numY_1 };
		int[] newIndexInSmallX = { curPRNode.getIndexInSmallCell()[0], midSmallIndex[0] - 1, midSmallIndex[0],
				curPRNode.getIndexInSmallCell()[1] };
		int[] newIndexInSmallY = { curPRNode.getIndexInSmallCell()[2], midSmallIndex[1] - 1, midSmallIndex[1],
				curPRNode.getIndexInSmallCell()[3] };

		// Init each small bucket (4 in total)
		ArrayList[][] pointsForS = new ArrayList[2][2];
		for (int i = 0; i < 2; i++)
			for (int j = 0; j < 2; j++) {
				pointsForS[i][j] = new ArrayList<MetricObjectMore>();
			}
		// new Coordinate Generated
		float midCoorX = largeCellCoor[0] + curPRNode.getSmallCellSize() * midSmallIndex[0];
		float midCoorY = largeCellCoor[2] + curPRNode.getSmallCellSize() * midSmallIndex[1];
		float[] newCoorX = { curPRNode.getXyCoordinates()[0], midCoorX, curPRNode.getXyCoordinates()[1] };
		float[] newCoorY = { curPRNode.getXyCoordinates()[2], midCoorY, curPRNode.getXyCoordinates()[3] };

		ArrayList<MetricObjectMore> listOfPoints = mapQuadInternalWithPoints.get(curPRNode);
		for (MetricObjectMore mo : listOfPoints) {
			int[] tempIndex = mo.getIndexForSmallCell();
			int tempIndexX = (tempIndex[0] < midSmallIndex[0]) ? 0 : 1;
			int tempIndexY = (tempIndex[1] < midSmallIndex[1]) ? 0 : 1;
			pointsForS[tempIndexX][tempIndexY].add(mo);
		}

		// seperate each small bucket
		for (int i = 0; i < 2; i++)
			for (int j = 0; j < 2; j++) {
				if (numCellsPerDimX[i] <= 0 || numCellsPerDimY[j] <= 0) {
					continue;
				}
				// check size and num of points inside, if size too small or num
				// of points small, don't divide
				if ((numCellsPerDimX[i] == 1) && (numCellsPerDimY[j] == 1)) {
					// check if can prune
					boolean canPrune = false;
					float[] newConstructed = { newCoorX[i], newCoorX[i + 1], newCoorY[j], newCoorY[j + 1] };
					int[] newIndexInsideSmall = { newIndexInSmallX[2 * i], newIndexInSmallX[2 * i + 1],
							newIndexInSmallY[2 * j], newIndexInSmallY[2 * j + 1] };

					prQuadLeafMore newLeaf = new prQuadLeafMore(newConstructed, newIndexInsideSmall, curPRNode,
							numCellsPerDimX[i], numCellsPerDimY[j], curPRNode.getSmallCellSize(), pointsForS[i][j],
							pointsForS[i][j].size(), canPrune);
					if (pointsForS[i][j].size() > 0) {
						curPRNode.addNewChild(newLeaf);
						// context.getCounter(Counters.SmallCellsNum).increment(1);
					}
					if ((!canPrune) && (pointsForS[i][j].size() > 0))
						prLeaves.add(newLeaf);
					count = count + pointsForS[i][j].size();
					// System.out.println("Get node: " +
					// newLeaf.printQuadLeaf());
				} // end if((numCellsPerDimX[i]==1) && (numCellsPerDimY[j]==1))
				else if (pointsForS[i][j].size() < K + 1) {
					// create a leaf no matter the size of the bucket
					float[] newConstructed = { newCoorX[i], newCoorX[i + 1], newCoorY[j], newCoorY[j + 1] };
					int[] newIndexInsideSmall = { newIndexInSmallX[2 * i], newIndexInSmallX[2 * i + 1],
							newIndexInSmallY[2 * j], newIndexInSmallY[2 * j + 1] };
					prQuadLeafMore newLeaf = new prQuadLeafMore(newConstructed, newIndexInsideSmall, curPRNode,
							numCellsPerDimX[i], numCellsPerDimY[j], curPRNode.getSmallCellSize(), pointsForS[i][j],
							pointsForS[i][j].size(), false);
					if (pointsForS[i][j].size() > 0) {
						curPRNode.addNewChild(newLeaf);
						// context.getCounter(Counters.SmallCellsNum).increment(1);
					}
					if (pointsForS[i][j].size() > 0)
						prLeaves.add(newLeaf);
					count = count + pointsForS[i][j].size();
					// System.out.println("Get node: " +
					// newLeaf.printQuadLeaf());
				} else {
					// create a internal node and add the arraylist to the
					// hashmap structure
					float[] newConstructed = { newCoorX[i], newCoorX[i + 1], newCoorY[j], newCoorY[j + 1] };
					int[] newIndexInsideSmall = { newIndexInSmallX[2 * i], newIndexInSmallX[2 * i + 1],
							newIndexInSmallY[2 * j], newIndexInSmallY[2 * j + 1] };
					prQuadInternalMore NewLeaf = new prQuadInternalMore(newConstructed, newIndexInsideSmall, curPRNode,
							numCellsPerDimX[i], numCellsPerDimY[j], curPRNode.getSmallCellSize());
					curPRNode.addNewChild(NewLeaf);
					mapQuadInternalWithPoints.put(NewLeaf, pointsForS[i][j]);
					prQuadTree.push(NewLeaf);
					// System.out.println("Get node: " +
					// NewLeaf.printQuadInternal());
				} // end else
			} // end for
		// mapQuadInternalWithPoints.remove(curPRNode);

	}

	public boolean areaInsideSafeArea(float[] safeArea, float[] extendedArea) {
		if (extendedArea[0] >= safeArea[0] && extendedArea[1] <= safeArea[1] && extendedArea[2] >= safeArea[2]
				&& extendedArea[3] <= safeArea[3])
			return true;
		else
			return false;
	}

	public int[] getIndexInSmallCell() {
		return indexInSmallCell;
	}

	public void setIndexInSmallCell(int[] indexInSmallCell) {
		this.indexInSmallCell = indexInSmallCell;
	}

	public float[] getXyCoordinates() {
		return xyCoordinates;
	}

	public void setXyCoordinates(float[] xyCoordinates) {
		this.xyCoordinates = xyCoordinates;
	}

	public boolean checkIfParentNull() {
		if (parentNode == null)
			return true;
		else
			return false;
	}

	public prQuadNodeMore getParentNode() {
		return parentNode;
	}

	public void setParentNode(prQuadNodeMore parentNode) {
		this.parentNode = parentNode;
	}

	public int getNumSmallCellX() {
		return numSmallCellX;
	}

	public void setNumSmallCellX(int numSmallCellX) {
		this.numSmallCellX = numSmallCellX;
	}

	public int getNumSmallCellY() {
		return numSmallCellY;
	}

	public void setNumSmallCellY(int numSmallCellY) {
		this.numSmallCellY = numSmallCellY;
	}

	public float getSmallCellSize() {
		return smallCellSize;
	}

	public void setSmallCellSize(float smallCellSize) {
		this.smallCellSize = smallCellSize;
	}
}
