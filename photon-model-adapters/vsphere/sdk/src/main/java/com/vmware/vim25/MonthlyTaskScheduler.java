
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for MonthlyTaskScheduler complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="MonthlyTaskScheduler"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DailyTaskScheduler"&gt;
 *       &lt;sequence&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "MonthlyTaskScheduler")
@XmlSeeAlso({
    MonthlyByDayTaskScheduler.class,
    MonthlyByWeekdayTaskScheduler.class
})
public class MonthlyTaskScheduler
    extends DailyTaskScheduler
{


}
