/**
  * This application counts the number of valid paths through an n x m matrix. The
  * starting cell is located at (0, 0) and finishing cell at (n, m). A path can only be composed
  * of adjacent cells containing a 1 and located immediately to the right or below.
  *
  * A sample landscape is provided. This application could easily be extended to accommodate
  * different landscapes.
 */
object TraversalApp {

  def main(args: Array[String]): Unit = {
    // Build the matrix
    val landscape = buildLandscape()

    // Build the tree
    val landscapeTree = new TraversalTree(None, None, false)
    if (landscape(1)(0) > 0) {
      landscapeTree.setDownNode(buildTree(landscape, 1, 0))
    }
    if (landscape(0)(1) > 0) {
      landscapeTree.setRightNode(buildTree(landscape, 0, 1))
    }

    // Count the number of end-state leaf nodes
    val validPaths = countEndLeaves(landscapeTree.getDownNode) +
      countEndLeaves(landscapeTree.getRightNode)

    println("Number of valid paths: " + validPaths)
  }

  /**
    * Traverses all possible paths through the provided landscape and represents them as a binary tree
    *
    * @param landscape a matrix to be transformed into a binary tree of valid paths to the finish state
    * @param row coordinate of the current location
    * @param col coordinate of the current location
    * @return a tree representing all paths through the landscape
    */
  def buildTree(landscape: Array[Array[Int]], row: Int, col: Int) : Option[TraversalTree] = {
    if (row > landscape.length || col > landscape(row).length) {
      return None
    }

    val node = new TraversalTree(None, None, false)

    if (row == landscape.length - 1 && col == landscape(0).length - 1) {
      node.setFinish(true)
    } else {
      if (col + 1 < landscape(row).length && landscape(row)(col + 1) > 0) {
        node.setRightNode(buildTree(landscape, row, col + 1))
      }
      if (row + 1 < landscape.length && landscape(row + 1)(col) > 0) {
        node.setDownNode(buildTree(landscape, row + 1, col))
      }
    }

    Some(node)
  }

  /**
    * Sums the number of leaves in the provided tree that are designated as finish states.
    *
    * @param binary tree of valid paths through a landscape
    * @return count of finish-state leaves
   */
  def countEndLeaves(tree: Option[TraversalTree]) : Int =  {
    if (tree.isDefined) {
      if (tree.get.getFinish) {
        1
      } else if (tree.get.getDownNode.isDefined || tree.get.getRightNode.isDefined) {
        countEndLeaves(tree.get.getDownNode) + countEndLeaves(tree.get.getRightNode)
      } else {
          0
      }
    } else {
      0
    }
  }


  /**
    * Builds a static 4 x 6 array that looks like this:
    *
    *  ----------------
    * |1, 1, 0, 1, 1, 0|
    * |1, 1, 1, 0, 1, 0|
    * |1, 0, 1, 1, 1, 1|
    * |1, 1, 1, 1, 1, 1|
    *  ----------------
    *
    * @return the landscape represented by an n x m array
    */
  def buildLandscape(): Array[Array[Int]] = {
    val landscape = Array.ofDim[Int](4, 6)

    landscape(0) = Array(1, 1, 0, 1, 1, 0)
    landscape(1) = Array(1, 1, 1, 0, 1, 0)
    landscape(2) = Array(1, 0, 1, 1, 1, 1)
    landscape(3) = Array(1, 1, 1, 1, 1, 1)

    landscape
  }
}


