
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VchaClusterRuntimeInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VchaClusterRuntimeInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="clusterState" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="nodeInfo" type="{urn:vim25}VchaNodeRuntimeInfo" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="clusterMode" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VchaClusterRuntimeInfo", propOrder = {
    "clusterState",
    "nodeInfo",
    "clusterMode"
})
public class VchaClusterRuntimeInfo
    extends DynamicData
{

    @XmlElement(required = true)
    protected String clusterState;
    protected List<VchaNodeRuntimeInfo> nodeInfo;
    @XmlElement(required = true)
    protected String clusterMode;

    /**
     * Gets the value of the clusterState property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getClusterState() {
        return clusterState;
    }

    /**
     * Sets the value of the clusterState property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setClusterState(String value) {
        this.clusterState = value;
    }

    /**
     * Gets the value of the nodeInfo property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the nodeInfo property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getNodeInfo().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link VchaNodeRuntimeInfo }
     * 
     * 
     */
    public List<VchaNodeRuntimeInfo> getNodeInfo() {
        if (nodeInfo == null) {
            nodeInfo = new ArrayList<VchaNodeRuntimeInfo>();
        }
        return this.nodeInfo;
    }

    /**
     * Gets the value of the clusterMode property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getClusterMode() {
        return clusterMode;
    }

    /**
     * Sets the value of the clusterMode property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setClusterMode(String value) {
        this.clusterMode = value;
    }

}
