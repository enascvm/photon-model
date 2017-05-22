
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for NodeDeploymentSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="NodeDeploymentSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="esxHost" type="{urn:vim25}ManagedObjectReference" minOccurs="0"/&gt;
 *         &lt;element name="datastore" type="{urn:vim25}ManagedObjectReference" minOccurs="0"/&gt;
 *         &lt;element name="publicNetworkPortGroup" type="{urn:vim25}ManagedObjectReference" minOccurs="0"/&gt;
 *         &lt;element name="clusterNetworkPortGroup" type="{urn:vim25}ManagedObjectReference" minOccurs="0"/&gt;
 *         &lt;element name="folder" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="resourcePool" type="{urn:vim25}ManagedObjectReference" minOccurs="0"/&gt;
 *         &lt;element name="managementVc" type="{urn:vim25}ServiceLocator" minOccurs="0"/&gt;
 *         &lt;element name="nodeName" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="ipSettings" type="{urn:vim25}CustomizationIPSettings"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "NodeDeploymentSpec", propOrder = {
    "esxHost",
    "datastore",
    "publicNetworkPortGroup",
    "clusterNetworkPortGroup",
    "folder",
    "resourcePool",
    "managementVc",
    "nodeName",
    "ipSettings"
})
@XmlSeeAlso({
    PassiveNodeDeploymentSpec.class
})
public class NodeDeploymentSpec
    extends DynamicData
{

    protected ManagedObjectReference esxHost;
    protected ManagedObjectReference datastore;
    protected ManagedObjectReference publicNetworkPortGroup;
    protected ManagedObjectReference clusterNetworkPortGroup;
    @XmlElement(required = true)
    protected ManagedObjectReference folder;
    protected ManagedObjectReference resourcePool;
    protected ServiceLocator managementVc;
    @XmlElement(required = true)
    protected String nodeName;
    @XmlElement(required = true)
    protected CustomizationIPSettings ipSettings;

    /**
     * Gets the value of the esxHost property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getEsxHost() {
        return esxHost;
    }

    /**
     * Sets the value of the esxHost property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setEsxHost(ManagedObjectReference value) {
        this.esxHost = value;
    }

    /**
     * Gets the value of the datastore property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getDatastore() {
        return datastore;
    }

    /**
     * Sets the value of the datastore property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setDatastore(ManagedObjectReference value) {
        this.datastore = value;
    }

    /**
     * Gets the value of the publicNetworkPortGroup property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getPublicNetworkPortGroup() {
        return publicNetworkPortGroup;
    }

    /**
     * Sets the value of the publicNetworkPortGroup property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setPublicNetworkPortGroup(ManagedObjectReference value) {
        this.publicNetworkPortGroup = value;
    }

    /**
     * Gets the value of the clusterNetworkPortGroup property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getClusterNetworkPortGroup() {
        return clusterNetworkPortGroup;
    }

    /**
     * Sets the value of the clusterNetworkPortGroup property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setClusterNetworkPortGroup(ManagedObjectReference value) {
        this.clusterNetworkPortGroup = value;
    }

    /**
     * Gets the value of the folder property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getFolder() {
        return folder;
    }

    /**
     * Sets the value of the folder property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setFolder(ManagedObjectReference value) {
        this.folder = value;
    }

    /**
     * Gets the value of the resourcePool property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getResourcePool() {
        return resourcePool;
    }

    /**
     * Sets the value of the resourcePool property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setResourcePool(ManagedObjectReference value) {
        this.resourcePool = value;
    }

    /**
     * Gets the value of the managementVc property.
     * 
     * @return
     *     possible object is
     *     {@link ServiceLocator }
     *     
     */
    public ServiceLocator getManagementVc() {
        return managementVc;
    }

    /**
     * Sets the value of the managementVc property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServiceLocator }
     *     
     */
    public void setManagementVc(ServiceLocator value) {
        this.managementVc = value;
    }

    /**
     * Gets the value of the nodeName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getNodeName() {
        return nodeName;
    }

    /**
     * Sets the value of the nodeName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setNodeName(String value) {
        this.nodeName = value;
    }

    /**
     * Gets the value of the ipSettings property.
     * 
     * @return
     *     possible object is
     *     {@link CustomizationIPSettings }
     *     
     */
    public CustomizationIPSettings getIpSettings() {
        return ipSettings;
    }

    /**
     * Sets the value of the ipSettings property.
     * 
     * @param value
     *     allowed object is
     *     {@link CustomizationIPSettings }
     *     
     */
    public void setIpSettings(CustomizationIPSettings value) {
        this.ipSettings = value;
    }

}
