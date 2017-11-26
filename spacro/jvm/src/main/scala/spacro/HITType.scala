package spacro

// TODO: create a replacement for turk's QualificationRequirement
// and move this and that to shared code
import com.amazonaws.services.mturk.model.QualificationRequirement

/** Represents all of the fields necessary to produce a HIT type.
  *
  * HIT Types define classes of HITs that are grouped together in the Mechanical Turk interface.
  * A worker does not so much choose to work on an individual HIT as an individual HIT Type.
  * After they complete one HIT, they will automatically be shown another of the same HIT Type,
  * and if they so choose, they can automatically accept that next HIT.
  *
  * From the requester's perspective,
  * many operations with the RequesterService (such as searching and finding reviewable HITs)
  * will be desired to apply only to HITs of a particular HIT type.
  * We do this using the HIT Type ID of the HIT type,
  * computed by Amazon and returned by the register() method on a HIT Type object.
  * This gives us a way to segregate our interactions with the API between different tasks,
  * allowing for running two different kinds of tasks in parallel
  * (for example, a Q/A gathering task and validation task).
  *
  * The HIT Type ID of a HIT is determined (automatically, by Amazon) on the basis of a set of values:
  * its title, description, keyword, rewards, auto-approval delay, assignment duration,
  * qualification requirements, assignment review policy, and HIT review policy.
  * We don't bother with assignment review policies or HIT review policies,
  * which are MTurk's way of automating assignment review; instead, we just review them
  * here in the code.
  * The rest of these fields are left for an instance to specify.
  * If you wish tasks to be segregated / run separately,
  * make sure their HIT Types differ in at least one of those fields.
  *
  * @param title the title of the HIT type; one of the first things workers see when browsing
  * @param description a longer (but still short) description of the task, displayed when a worker clicks on the HIT group while browsing
  * @param keywords comma-separated keywords, helping workers find the task through search
  * @param reward the payment, in dollars, to a worker for having an assignment approved
  * @param autoApprovalDelay the time, in seconds, before MTurk will automatically approve an assignment if you do not approve/reject it manually
  * @param assignmentDuration The time, in seconds, allowed for a worker to complete a single assignment
  * @param The qualification requirements a worker must satisfy to work on the task
  */
case class HITType(
  title: String,
  description: String,
  keywords: String,
  reward: Double,
  autoApprovalDelay: Long = 3600L, // seconds (1 hour)
  assignmentDuration: Long = 600L, // seconds (10 minutes)
  qualRequirements: Array[QualificationRequirement] = Array.empty[QualificationRequirement]
)
