/**
  * Created by michelle on 5/15/16.
  */
class TraversalTree(down: Option[TraversalTree], right: Option[TraversalTree], finish: Boolean) {
  private var downNode = down
  private var rightNode = right
  private var isFinish = finish

  def getDownNode = downNode
  def setDownNode(node: Option[TraversalTree]) = {
    downNode = node
  }

  def getRightNode = rightNode
  def setRightNode(node: Option[TraversalTree]) = {
    rightNode = node
  }

  def getFinish = isFinish
  def setFinish(finish: Boolean) = {
    isFinish = finish
  }
}
