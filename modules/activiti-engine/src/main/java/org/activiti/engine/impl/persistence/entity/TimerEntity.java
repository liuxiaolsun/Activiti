/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.activiti.engine.impl.persistence.entity;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.activiti.engine.ActivitiException;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.delegate.VariableScope;
import org.activiti.engine.impl.calendar.BusinessCalendar;
import org.activiti.engine.impl.calendar.CycleBusinessCalendar;
import org.activiti.engine.impl.context.Context;
import org.activiti.engine.impl.el.NoExecutionVariableScope;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.jobexecutor.TimerDeclarationImpl;
import org.activiti.engine.impl.jobexecutor.TimerExecuteNestedActivityJobHandler;
import org.activiti.engine.impl.util.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author Tom Baeyens
 */
public class TimerEntity extends JobEntity {

  private static final long serialVersionUID = 1L;

  private static Logger log = LoggerFactory.getLogger(TimerEntity.class);

  protected String repeat;
  protected Date endDate;

  public TimerEntity() {
  }

  public TimerEntity(TimerDeclarationImpl timerDeclaration) {
    jobHandlerType = timerDeclaration.getJobHandlerType();
    jobHandlerConfiguration = timerDeclaration.getJobHandlerConfiguration();
    isExclusive = timerDeclaration.isExclusive();
    repeat = timerDeclaration.getRepeat();
    retries = timerDeclaration.getRetries();
  }

  private TimerEntity(TimerEntity te) {
    jobHandlerConfiguration = te.jobHandlerConfiguration;
    jobHandlerType = te.jobHandlerType;
    isExclusive = te.isExclusive;
    repeat = te.repeat;
    retries = te.retries;
    endDate = te.endDate;
    executionId = te.executionId;
    processInstanceId = te.processInstanceId;
    processDefinitionId = te.processDefinitionId;

    // Inherit tenant
    tenantId = te.tenantId;
  }

  @Override
  public void execute(CommandContext commandContext) {

    super.execute(commandContext);

    //set endDate if it was set to the definition
    if (jobHandlerType.equalsIgnoreCase(TimerExecuteNestedActivityJobHandler.TYPE)) {
      JSONObject cfgJson = new JSONObject(jobHandlerConfiguration);
      String nestedActivityId = (String) cfgJson.get(TimerExecuteNestedActivityJobHandler.PROPERTYNAME_TIMER_ACTIVITY_ID);
      if (cfgJson.has(TimerExecuteNestedActivityJobHandler.PROPERTYNAME_END_DATE_EXPRESSION)) {
        String endDateExpressionString = (String) cfgJson.get(TimerExecuteNestedActivityJobHandler.PROPERTYNAME_END_DATE_EXPRESSION);
        Expression endDateExpression = Context.getProcessEngineConfiguration().getExpressionManager().createExpression(endDateExpressionString);

        String endDateString = null;

        BusinessCalendar businessCalendar = Context.getProcessEngineConfiguration().getBusinessCalendarManager().getBusinessCalendar(CycleBusinessCalendar.NAME);

        VariableScope executionEntity = commandContext.getExecutionEntityManager().findExecutionById(this.getExecutionId());
        if (executionEntity == null) {
          executionEntity = NoExecutionVariableScope.getSharedInstance();
        }

        if (endDateExpression != null) {
          Object endDateValue = endDateExpression.getValue(executionEntity);
          if (endDateValue instanceof String) {
            endDateString = (String) endDateValue;
          } else if (endDateValue instanceof Date) {
            endDate = (Date) endDateValue;
          } else {
            throw new ActivitiException("Timer '" + ((ExecutionEntity) executionEntity).getActivityId() + "' was not configured with a valid duration/time, either hand in a java.util.Date or a String in format 'yyyy-MM-dd'T'hh:mm:ss'");
          }

          if (endDate == null) {
            endDate = businessCalendar.resolveEndDate(endDateString);
          }
        }
      }
    }
    
    if (repeat == null) {

      if (log.isDebugEnabled()) {
        log.debug("Timer {} fired. Deleting timer.", getId());
      }
      delete();
    } else {
      delete();
      int repeatValue = calculateRepeatValue();
      if (repeatValue != 0) {
        if (repeatValue > 0) {
          setNewRepeat(repeatValue);
        }
        Date newTimer = calculateRepeat();
        if (newTimer != null && isValidTime(newTimer)) {
          TimerEntity te = new TimerEntity(this);
          te.setDuedate(newTimer);
          Context
              .getCommandContext()
              .getJobEntityManager()
              .schedule(te);
        }
      }
    }

  }

  private boolean isValidTime(Date newTimer) {
    BusinessCalendar businessCalendar = Context
        .getProcessEngineConfiguration()
        .getBusinessCalendarManager()
        .getBusinessCalendar(CycleBusinessCalendar.NAME);
    return businessCalendar.validateDuedate(repeat , endDate, newTimer);
  }

  private int calculateRepeatValue() {
    int times = -1;
    List<String> expression = Arrays.asList(repeat.split("/"));
    if (expression.size() > 1 && expression.get(0).startsWith("R") && expression.get(0).length() > 1) {
      times = Integer.parseInt(expression.get(0).substring(1));
      if (times > 0) {
        times--;
      }
    }
    return times;
  }
  
  private void setNewRepeat(int newRepeatValue) {
    List<String> expression = Arrays.asList(repeat.split("/"));
    expression = expression.subList(1, expression.size());
    StringBuilder repeatBuilder = new StringBuilder("R");
    repeatBuilder.append(newRepeatValue);
    for (String value : expression) {
      repeatBuilder.append("/");
      repeatBuilder.append(value);
    }
    repeat = repeatBuilder.toString();
  }

  private Date calculateRepeat() {
    BusinessCalendar businessCalendar = Context
        .getProcessEngineConfiguration()
        .getBusinessCalendarManager()
        .getBusinessCalendar(CycleBusinessCalendar.NAME);
    return businessCalendar.resolveDuedate(repeat);
  }

  public String getRepeat() {
    return repeat;
  }

  public void setRepeat(String repeat) {
    this.repeat = repeat;
  }

  public Date getEndDate() {
    return endDate;
  }

  public void setEndDate(Date endDate) {
    this.endDate = endDate;
  }
}
