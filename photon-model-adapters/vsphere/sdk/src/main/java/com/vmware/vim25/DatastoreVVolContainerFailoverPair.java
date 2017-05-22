
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DatastoreVVolContainerFailoverPair complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DatastoreVVolContainerFailoverPair"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="srcContainer" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="tgtContainer" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="vvolMapping" type="{urn:vim25}KeyValue" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DatastoreVVolContainerFailoverPair", propOrder = {
    "srcContainer",
    "tgtContainer",
    "vvolMapping"
})
public class DatastoreVVolContainerFailoverPair
    extends DynamicData
{

    protected String srcContainer;
    @XmlElement(required = true)
    protected String tgtContainer;
    protected List<KeyValue> vvolMapping;

    /**
     * Gets the value of the srcContainer property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSrcContainer() {
        return srcContainer;
    }

    /**
     * Sets the value of the srcContainer property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSrcContainer(String value) {
        this.srcContainer = value;
    }

    /**
     * Gets the value of the tgtContainer property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTgtContainer() {
        return tgtContainer;
    }

    /**
     * Sets the value of the tgtContainer property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTgtContainer(String value) {
        this.tgtContainer = value;
    }

    /**
     * Gets the value of the vvolMapping property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the vvolMapping property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getVvolMapping().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link KeyValue }
     * 
     * 
     */
    public List<KeyValue> getVvolMapping() {
        if (vvolMapping == null) {
            vvolMapping = new ArrayList<KeyValue>();
        }
        return this.vvolMapping;
    }

}
