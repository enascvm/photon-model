
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.DynamicData;
import com.vmware.vim25.KeyAnyValue;


/**
 * <p>Java class for PbmExtendedElementDescription complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmExtendedElementDescription"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="label" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="summary" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="key" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="messageCatalogKeyPrefix" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="messageArg" type="{urn:vim25}KeyAnyValue" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmExtendedElementDescription", propOrder = {
    "label",
    "summary",
    "key",
    "messageCatalogKeyPrefix",
    "messageArg"
})
public class PbmExtendedElementDescription
    extends DynamicData
{

    @XmlElement(required = true)
    protected String label;
    @XmlElement(required = true)
    protected String summary;
    @XmlElement(required = true)
    protected String key;
    @XmlElement(required = true)
    protected String messageCatalogKeyPrefix;
    protected List<KeyAnyValue> messageArg;

    /**
     * Gets the value of the label property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getLabel() {
        return label;
    }

    /**
     * Sets the value of the label property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setLabel(String value) {
        this.label = value;
    }

    /**
     * Gets the value of the summary property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSummary() {
        return summary;
    }

    /**
     * Sets the value of the summary property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSummary(String value) {
        this.summary = value;
    }

    /**
     * Gets the value of the key property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getKey() {
        return key;
    }

    /**
     * Sets the value of the key property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setKey(String value) {
        this.key = value;
    }

    /**
     * Gets the value of the messageCatalogKeyPrefix property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMessageCatalogKeyPrefix() {
        return messageCatalogKeyPrefix;
    }

    /**
     * Sets the value of the messageCatalogKeyPrefix property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMessageCatalogKeyPrefix(String value) {
        this.messageCatalogKeyPrefix = value;
    }

    /**
     * Gets the value of the messageArg property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the messageArg property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getMessageArg().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link KeyAnyValue }
     * 
     * 
     */
    public List<KeyAnyValue> getMessageArg() {
        if (messageArg == null) {
            messageArg = new ArrayList<KeyAnyValue>();
        }
        return this.messageArg;
    }

}
