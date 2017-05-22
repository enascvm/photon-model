
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DvsPortReconfiguredEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DvsPortReconfiguredEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DvsEvent"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="portKey" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded"/&gt;
 *         &lt;element name="configChanges" type="{urn:vim25}ChangesInfoEventArgument" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DvsPortReconfiguredEvent", propOrder = {
    "portKey",
    "configChanges"
})
public class DvsPortReconfiguredEvent
    extends DvsEvent
{

    @XmlElement(required = true)
    protected List<String> portKey;
    protected List<ChangesInfoEventArgument> configChanges;

    /**
     * Gets the value of the portKey property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the portKey property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPortKey().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getPortKey() {
        if (portKey == null) {
            portKey = new ArrayList<String>();
        }
        return this.portKey;
    }

    /**
     * Gets the value of the configChanges property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the configChanges property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getConfigChanges().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ChangesInfoEventArgument }
     * 
     * 
     */
    public List<ChangesInfoEventArgument> getConfigChanges() {
        if (configChanges == null) {
            configChanges = new ArrayList<ChangesInfoEventArgument>();
        }
        return this.configChanges;
    }

}
