package chatbot
import java.beans.BeanDescriptor
import java.beans.IntrospectionException
import java.beans.PropertyDescriptor
import java.beans.SimpleBeanInfo

class ChatHistoryPanelBeanInfo : SimpleBeanInfo() {

    override fun getBeanDescriptor() =
        BeanDescriptor(ChatHistoryPanel::class.java).apply {
            displayName = "Chat History Panel"
        }

    override fun getPropertyDescriptors(): Array<PropertyDescriptor> = try {
        arrayOf(
            PropertyDescriptor("onBranchRequested", ChatHistoryPanel::class.java)
                .apply { isHidden = true }
        )
    } catch (e: IntrospectionException) {
        emptyArray()
    }
}