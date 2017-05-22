
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterConfigInfoEx complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ClusterConfigInfoEx"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}ComputeResourceConfigInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="dasConfig" type="{urn:vim25}ClusterDasConfigInfo"/&gt;
 *         &lt;element name="dasVmConfig" type="{urn:vim25}ClusterDasVmConfigInfo" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="drsConfig" type="{urn:vim25}ClusterDrsConfigInfo"/&gt;
 *         &lt;element name="drsVmConfig" type="{urn:vim25}ClusterDrsVmConfigInfo" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="rule" type="{urn:vim25}ClusterRuleInfo" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="orchestration" type="{urn:vim25}ClusterOrchestrationInfo" minOccurs="0"/&gt;
 *         &lt;element name="vmOrchestration" type="{urn:vim25}ClusterVmOrchestrationInfo" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="dpmConfigInfo" type="{urn:vim25}ClusterDpmConfigInfo" minOccurs="0"/&gt;
 *         &lt;element name="dpmHostConfig" type="{urn:vim25}ClusterDpmHostConfigInfo" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="vsanConfigInfo" type="{urn:vim25}VsanClusterConfigInfo" minOccurs="0"/&gt;
 *         &lt;element name="vsanHostConfig" type="{urn:vim25}VsanHostConfigInfo" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="group" type="{urn:vim25}ClusterGroupInfo" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="infraUpdateHaConfig" type="{urn:vim25}ClusterInfraUpdateHaConfigInfo" minOccurs="0"/&gt;
 *         &lt;element name="proactiveDrsConfig" type="{urn:vim25}ClusterProactiveDrsConfigInfo" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ClusterConfigInfoEx", propOrder = {
    "dasConfig",
    "dasVmConfig",
    "drsConfig",
    "drsVmConfig",
    "rule",
    "orchestration",
    "vmOrchestration",
    "dpmConfigInfo",
    "dpmHostConfig",
    "vsanConfigInfo",
    "vsanHostConfig",
    "group",
    "infraUpdateHaConfig",
    "proactiveDrsConfig"
})
public class ClusterConfigInfoEx
    extends ComputeResourceConfigInfo
{

    @XmlElement(required = true)
    protected ClusterDasConfigInfo dasConfig;
    protected List<ClusterDasVmConfigInfo> dasVmConfig;
    @XmlElement(required = true)
    protected ClusterDrsConfigInfo drsConfig;
    protected List<ClusterDrsVmConfigInfo> drsVmConfig;
    protected List<ClusterRuleInfo> rule;
    protected ClusterOrchestrationInfo orchestration;
    protected List<ClusterVmOrchestrationInfo> vmOrchestration;
    protected ClusterDpmConfigInfo dpmConfigInfo;
    protected List<ClusterDpmHostConfigInfo> dpmHostConfig;
    protected VsanClusterConfigInfo vsanConfigInfo;
    protected List<VsanHostConfigInfo> vsanHostConfig;
    protected List<ClusterGroupInfo> group;
    protected ClusterInfraUpdateHaConfigInfo infraUpdateHaConfig;
    protected ClusterProactiveDrsConfigInfo proactiveDrsConfig;

    /**
     * Gets the value of the dasConfig property.
     * 
     * @return
     *     possible object is
     *     {@link ClusterDasConfigInfo }
     *     
     */
    public ClusterDasConfigInfo getDasConfig() {
        return dasConfig;
    }

    /**
     * Sets the value of the dasConfig property.
     * 
     * @param value
     *     allowed object is
     *     {@link ClusterDasConfigInfo }
     *     
     */
    public void setDasConfig(ClusterDasConfigInfo value) {
        this.dasConfig = value;
    }

    /**
     * Gets the value of the dasVmConfig property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the dasVmConfig property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDasVmConfig().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ClusterDasVmConfigInfo }
     * 
     * 
     */
    public List<ClusterDasVmConfigInfo> getDasVmConfig() {
        if (dasVmConfig == null) {
            dasVmConfig = new ArrayList<ClusterDasVmConfigInfo>();
        }
        return this.dasVmConfig;
    }

    /**
     * Gets the value of the drsConfig property.
     * 
     * @return
     *     possible object is
     *     {@link ClusterDrsConfigInfo }
     *     
     */
    public ClusterDrsConfigInfo getDrsConfig() {
        return drsConfig;
    }

    /**
     * Sets the value of the drsConfig property.
     * 
     * @param value
     *     allowed object is
     *     {@link ClusterDrsConfigInfo }
     *     
     */
    public void setDrsConfig(ClusterDrsConfigInfo value) {
        this.drsConfig = value;
    }

    /**
     * Gets the value of the drsVmConfig property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the drsVmConfig property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDrsVmConfig().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ClusterDrsVmConfigInfo }
     * 
     * 
     */
    public List<ClusterDrsVmConfigInfo> getDrsVmConfig() {
        if (drsVmConfig == null) {
            drsVmConfig = new ArrayList<ClusterDrsVmConfigInfo>();
        }
        return this.drsVmConfig;
    }

    /**
     * Gets the value of the rule property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the rule property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getRule().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ClusterRuleInfo }
     * 
     * 
     */
    public List<ClusterRuleInfo> getRule() {
        if (rule == null) {
            rule = new ArrayList<ClusterRuleInfo>();
        }
        return this.rule;
    }

    /**
     * Gets the value of the orchestration property.
     * 
     * @return
     *     possible object is
     *     {@link ClusterOrchestrationInfo }
     *     
     */
    public ClusterOrchestrationInfo getOrchestration() {
        return orchestration;
    }

    /**
     * Sets the value of the orchestration property.
     * 
     * @param value
     *     allowed object is
     *     {@link ClusterOrchestrationInfo }
     *     
     */
    public void setOrchestration(ClusterOrchestrationInfo value) {
        this.orchestration = value;
    }

    /**
     * Gets the value of the vmOrchestration property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the vmOrchestration property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getVmOrchestration().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ClusterVmOrchestrationInfo }
     * 
     * 
     */
    public List<ClusterVmOrchestrationInfo> getVmOrchestration() {
        if (vmOrchestration == null) {
            vmOrchestration = new ArrayList<ClusterVmOrchestrationInfo>();
        }
        return this.vmOrchestration;
    }

    /**
     * Gets the value of the dpmConfigInfo property.
     * 
     * @return
     *     possible object is
     *     {@link ClusterDpmConfigInfo }
     *     
     */
    public ClusterDpmConfigInfo getDpmConfigInfo() {
        return dpmConfigInfo;
    }

    /**
     * Sets the value of the dpmConfigInfo property.
     * 
     * @param value
     *     allowed object is
     *     {@link ClusterDpmConfigInfo }
     *     
     */
    public void setDpmConfigInfo(ClusterDpmConfigInfo value) {
        this.dpmConfigInfo = value;
    }

    /**
     * Gets the value of the dpmHostConfig property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the dpmHostConfig property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDpmHostConfig().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ClusterDpmHostConfigInfo }
     * 
     * 
     */
    public List<ClusterDpmHostConfigInfo> getDpmHostConfig() {
        if (dpmHostConfig == null) {
            dpmHostConfig = new ArrayList<ClusterDpmHostConfigInfo>();
        }
        return this.dpmHostConfig;
    }

    /**
     * Gets the value of the vsanConfigInfo property.
     * 
     * @return
     *     possible object is
     *     {@link VsanClusterConfigInfo }
     *     
     */
    public VsanClusterConfigInfo getVsanConfigInfo() {
        return vsanConfigInfo;
    }

    /**
     * Sets the value of the vsanConfigInfo property.
     * 
     * @param value
     *     allowed object is
     *     {@link VsanClusterConfigInfo }
     *     
     */
    public void setVsanConfigInfo(VsanClusterConfigInfo value) {
        this.vsanConfigInfo = value;
    }

    /**
     * Gets the value of the vsanHostConfig property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the vsanHostConfig property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getVsanHostConfig().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link VsanHostConfigInfo }
     * 
     * 
     */
    public List<VsanHostConfigInfo> getVsanHostConfig() {
        if (vsanHostConfig == null) {
            vsanHostConfig = new ArrayList<VsanHostConfigInfo>();
        }
        return this.vsanHostConfig;
    }

    /**
     * Gets the value of the group property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the group property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getGroup().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ClusterGroupInfo }
     * 
     * 
     */
    public List<ClusterGroupInfo> getGroup() {
        if (group == null) {
            group = new ArrayList<ClusterGroupInfo>();
        }
        return this.group;
    }

    /**
     * Gets the value of the infraUpdateHaConfig property.
     * 
     * @return
     *     possible object is
     *     {@link ClusterInfraUpdateHaConfigInfo }
     *     
     */
    public ClusterInfraUpdateHaConfigInfo getInfraUpdateHaConfig() {
        return infraUpdateHaConfig;
    }

    /**
     * Sets the value of the infraUpdateHaConfig property.
     * 
     * @param value
     *     allowed object is
     *     {@link ClusterInfraUpdateHaConfigInfo }
     *     
     */
    public void setInfraUpdateHaConfig(ClusterInfraUpdateHaConfigInfo value) {
        this.infraUpdateHaConfig = value;
    }

    /**
     * Gets the value of the proactiveDrsConfig property.
     * 
     * @return
     *     possible object is
     *     {@link ClusterProactiveDrsConfigInfo }
     *     
     */
    public ClusterProactiveDrsConfigInfo getProactiveDrsConfig() {
        return proactiveDrsConfig;
    }

    /**
     * Sets the value of the proactiveDrsConfig property.
     * 
     * @param value
     *     allowed object is
     *     {@link ClusterProactiveDrsConfigInfo }
     *     
     */
    public void setProactiveDrsConfig(ClusterProactiveDrsConfigInfo value) {
        this.proactiveDrsConfig = value;
    }

}
