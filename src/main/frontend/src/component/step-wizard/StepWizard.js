import {html, css, LitElement} from 'lit';
import {stepWizardStyle} from './step-wizard-css.js'

class StepWizard extends LitElement {
    static styles = [css`
        :host {
            display: block;
            width: 100%;
            color: var(--step-wizard-text-color, #000);
        }
    `,
        stepWizardStyle
    ]

    static get is() {
        return 'step-wizard';
    }

    static get properties() {
        return {
            stepsJson: {type: String},
            steps: [],
            currentStep: {type: Number}
        }
    };

    constructor() {
        super();
    }

    getNext() {
        if (this.currentStep < this.steps.length) {
            this.currentStep += 1;
        }
    }

    getBack() {
        if (this.currentStep > 1) {
            this.currentStep -= 1;
        }
    }

    setStep(step) {
        if (step < this.steps.length && step > 0) {
            this.currentStep = step;
        }
    }

    setSteps() {
        this.steps = JSON.parse(this.stepsJson);
    }

    goToStep(step) {
        this.currentStep = step;

        const customEvent = new CustomEvent('step-wizard-changed', {detail: {value: step}});
        this.dispatchEvent(customEvent);
    }

    render() {
        const widthPercentage = 100 * (this.currentStep - 1) / (this.steps.length - 1);

        return html`
            <div class="step-wizard">
                <div class="progress-line">
                    <div class="progress-line-active" style="width: ${widthPercentage}%"></div>
                </div>
                <div class="step-container">
                    ${this.steps.map((step, index) => html`
                        <div class="step-block">
                            <div class="step ${this.currentStep === index + 1 ? 'active' : this.currentStep > index + 1 ? 'completed' : ''}"
                                 data-step="${index + 1}"> ${index + 1}
                            </div>
                            <span class="step-label step-${this.currentStep === index + 1 ? 'active' : this.currentStep > index + 1 ? 'completed' : ''}">${step.name} </span>
                        </div>
                    `)}
                </div>

            </div>
        `;
    }
}

customElements.define(StepWizard.is, StepWizard);

export {StepWizard};
