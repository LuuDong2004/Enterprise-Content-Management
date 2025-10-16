import { css } from 'lit';

const stepWizardStyle = css `
    .step-wizard {
        width: 100%;
        position: relative;
    }

    .progress-line {
        position: absolute;
        top: 15px;
        transform: translateY(-50%);
        height: 1px;
        background-color: #e0e0e0;
        width: calc(100% - 200px);
        left: 100px;
        z-index: 0;
    }

    .progress-line-active {
        position: absolute;
        top: 0;
        left: 0;
        height: 100%;
        background-color: var(--lumo-primary-color);
        transition: width 0.5s ease-in-out;
    }

    .step-container {
        display: flex;
        justify-content: space-between;
        align-items: center;
        position: relative;
        z-index: 1;
        padding: 0 50px;
    }

    .step-block {
        display: flex;
        flex-direction: column;
        align-items: center;
        width: 100px;
        text-align: center;
        position: relative;
    }

    .step {
        /* Box model */
        width: 2rem;
        height: 2rem;
        border: 1px solid var(--lumo-primary-color);
        border-radius: 14px;
        box-sizing: border-box;

        /* Positioning */
        position: relative;
        z-index: 1;

        /* Display and alignment */
        display: flex;
        align-items: center;
        justify-content: center;

        /* Colors */
        background-color: #fff;
        color: var(--lumo-primary-color);

        /* Typography */
        font-family: "InterVariable", serif;
        font-weight: 800;
        line-height: 1.25rem;
    }

    .step::before {
        content: '';
        position: absolute;
        top: -8px;
        left: -8px;
        right: -8px;
        bottom: -8px;
        border-radius: 20px;
        border: 3px solid transparent;
        transition: all 0.3s ease;
    }

    .step.active {
        font-size: 17px;
        font-weight: 500;
        line-height: 1.5rem;
        border-color: var(--lumo-primary-color);
        background-color: #fdbaba;
        color: #00544E;
    }


    .step.active::before {
        border-color: var(--lumo-primary-color);
    }

    .step.completed {
        border-color: #efadad;
        background-color: #f1dada;
        color: var(--lumo-primary-color);
    }

    .step-label {
        position: absolute;
        top: 45px;
        color: #505A5F;
        left: 50%;
        font-family: "InterVariable", serif;
        font-size: 0.75rem;
        line-height: 1rem;
        font-weight: 400;
        transform: translateX(-50%);
        white-space: nowrap;
    }

    .step-active, .step-completed {
        color: #00544E;
        font-weight: 500;
    }

    @media screen and (max-width: 480px) {
        .progress-line {
            width: calc(100% - 60px);
            left: 30px;
        }

        .step-container {
            padding: 0 15px;
        }

        .step-block {
            width: 60px;
        }

        .step {
            width: 1.5rem;
            height: 1.25rem;
        }

        .step-label {
            font-size: 0.6rem;
            top: 40px;
        }
    }
`;

export { stepWizardStyle };