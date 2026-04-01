package com.yiaobang.kserial.model

/**
 * 环形字节缓冲区 (1:1 复刻 Java CircularArray)
 * 用于在串口收发过程中平滑处理流式数据，防止粘包。
 * 
 * @param capacity 缓冲区最大容量
 */
class CircularArray(private val capacity: Int) {
    private val array = ByteArray(capacity)
    private var front = 0  // 头部指针
    private var rear = -1   // 尾部指针
    private var size = 0    // 当前已存储的数据大小

    /**
     * 向缓冲区添加单个字节
     */
    @Synchronized
    fun add(element: Byte) {
        if (size == capacity) {
            dequeue() // 如果满了，先移除最旧的一个
        }
        rear = (rear + 1) % capacity
        array[rear] = element
        size++
    }

    /**
     * 向缓冲区批量添加字节数组
     */
    @Synchronized
    fun add(elements: ByteArray) {
        if (elements.size > capacity) {
            throw IllegalArgumentException("数据大小超过了缓冲区容量")
        }

        // 计算需要挪出的空间
        val elementsToDequeue = (size + elements.size - capacity).coerceAtLeast(0)
        repeat(elementsToDequeue) {
            dequeue()
        }

        for (element in elements) {
            rear = (rear + 1) % capacity
            array[rear] = element
            size++
        }
    }

    /**
     * 获取指定索引位置的字节
     */
    @Synchronized
    fun getElement(index: Int): Byte {
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("索引超出缓冲区范围")
        }
        val actualIndex = (front + index) % capacity
        return array[actualIndex]
    }

    /**
     * 获取当前缓冲区内的所有有效字节
     */
    @Synchronized
    fun getElements(): ByteArray {
        val result = ByteArray(size)
        if (size == 0) return result

        if (rear >= front) {
            // 线性部分直接拷贝
            System.arraycopy(array, front, result, 0, size)
        } else {
            // 环形部分分段拷贝
            val frontSegmentLength = capacity - front
            System.arraycopy(array, front, result, 0, frontSegmentLength)
            System.arraycopy(array, 0, result, frontSegmentLength, size - frontSegmentLength)
        }
        return result
    }

    /**
     * 清空缓冲区
     */
    @Synchronized
    fun clear() {
        front = 0
        rear = -1
        size = 0
    }

    /**
     * 返回当前已存储的数据大小
     */
    fun size(): Int = size

    /**
     * 内部出队逻辑 (减小 size 并移动 front 指针)
     */
    private fun dequeue() {
        if (size == 0) return
        front = (front + 1) % capacity
        size--
    }
}
